package com.kazikonnect.backend.features.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final B2cPayoutService b2cPayoutService;

    public record StkPushRequest(UUID jobId, String phoneNumber) {
    }

    @PostMapping("/mpesa/stkpush")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<?> initiateStkPush(
            @RequestBody StkPushRequest request,
            Principal principal) {
        try {
            log.info("Initiating STK push for job: {} by user: {}", request.jobId(), principal.getName());
            StkPushResponse response = paymentService.initiateStkPush(request.jobId(), request.phoneNumber(),
                    principal);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("STK Push initiation failed: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("status", "FAILED");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(jobId));
    }

    @GetMapping("/receipt/{jobId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentReceipt(@PathVariable UUID jobId) {
        PaymentStatusResponse status = paymentService.getPaymentStatus(jobId);
        if (status.mpesaReceiptNumber() == null || status.mpesaReceiptNumber().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/escrow/release/{jobId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> releaseEscrow(@PathVariable UUID jobId, Principal principal) {
        paymentService.releaseEscrow(jobId, principal);
        return ResponseEntity.ok(Map.of("status", "RELEASED", "message", "Escrow released to worker wallet."));
    }

    @PostMapping("/escrow/refund/{jobId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> refundEscrow(@PathVariable UUID jobId, Principal principal) {
        paymentService.refundEscrow(jobId, principal);
        return ResponseEntity.ok(Map.of("status", "REFUNDED", "message", "Escrow refunded to client wallet."));
    }

    @GetMapping("/escrow/all")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<List<PaymentStatusResponse>> getAllEscrowPayments() {
        return ResponseEntity.ok(paymentService.getAllEscrowPayments());
    }

    @PostMapping("/mpesa/callback")
    public ResponseEntity<Map<String, String>> receiveMpesaCallback(
            @RequestBody MpesaCallbackRequest callbackRequest,
            HttpServletRequest servletRequest) {
        String remoteIp = CallbackIpResolver.resolve(
                servletRequest.getHeader("X-Forwarded-For"),
                servletRequest.getRemoteAddr());
        log.info("Received M-Pesa callback from IP: {}", remoteIp);
        paymentService.handleMpesaCallback(callbackRequest, remoteIp);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @PostMapping("/mpesa/result")
    public ResponseEntity<Map<String, String>> handleMpesaB2cResult(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String remoteIp = CallbackIpResolver.resolve(
                request.getHeader("X-Forwarded-For"),
                request.getRemoteAddr());
        log.info("MPESA B2C result received from {}: {}", remoteIp, payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            if (result == null) {
                log.warn("B2C result payload missing Result object from {}", remoteIp);
                return ResponseEntity.ok(Map.of("status", "received"));
            }

            String transactionId = (String) result.get("TransactionID");
            String resultCode = String.valueOf(result.get("ResultCode"));
            String conversationId = (String) result.get("ConversationID");
            String mpesaReceiptNumber = (String) result.get("MpesaReceiptNumber");
            
            log.info("B2C transaction {} (conversation: {}, receipt: {}) completed with code {}", 
                transactionId, conversationId, mpesaReceiptNumber, resultCode);
            
            if ("0".equals(resultCode) && conversationId != null) {
                b2cPayoutService.processB2cSuccess(transactionId, conversationId, mpesaReceiptNumber, remoteIp);
            } else {
                log.warn("B2C result failed with code {} for conversation {}", resultCode, conversationId);
            }
            
        } catch (Exception e) {
            log.error("Error processing B2C result: {}", e.getMessage(), e);
        }
        
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @PostMapping("/mpesa/timeout")
    public ResponseEntity<Map<String, String>> handleMpesaB2cTimeout(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String remoteIp = CallbackIpResolver.resolve(
                request.getHeader("X-Forwarded-For"),
                request.getRemoteAddr());
        log.warn("MPESA B2C timeout received from {}: {}", remoteIp, payload);
        
        try {
            String conversationId = extractB2cConversationId(payload);
            String responseCode = extractB2cField(payload, "ResponseCode");
            String responseDescription = extractB2cField(payload, "ResponseDescription");
            
            if (conversationId == null || conversationId.isBlank()) {
                log.warn("B2C timeout payload missing ConversationID from {}", remoteIp);
                return ResponseEntity.ok(Map.of("status", "received"));
            }
            
            log.error("B2C transaction {} timed out with code {}: {}", 
                conversationId, responseCode, responseDescription);
            
            b2cPayoutService.processB2cTimeout(conversationId, responseCode, responseDescription, remoteIp);
            
        } catch (Exception e) {
            log.error("Error processing B2C timeout: {}", e.getMessage(), e);
        }
        
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    private String extractB2cConversationId(Map<String, Object> payload) {
        Object root = payload.get("ConversationID");
        if (root != null) {
            return String.valueOf(root);
        }
        Object result = payload.get("Result");
        if (result instanceof Map<?, ?> resultMap) {
            Object nested = resultMap.get("ConversationID");
            if (nested != null) {
                return String.valueOf(nested);
            }
        }
        return null;
    }

    private String extractB2cField(Map<String, Object> payload, String field) {
        Object root = payload.get(field);
        if (root != null) {
            return String.valueOf(root);
        }
        Object result = payload.get("Result");
        if (result instanceof Map<?, ?> resultMap) {
            Object nested = resultMap.get(field);
            if (nested != null) {
                return String.valueOf(nested);
            }
        }
        return null;
    }
}
