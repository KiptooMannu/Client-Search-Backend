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

    public record StkPushRequest(UUID jobId, String phoneNumber) {
    }

    public record PaymentActionRequest(UUID jobId) {
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

    @GetMapping("/escrow/all")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<List<PaymentStatusResponse>> getAllEscrowPayments() {
        return ResponseEntity.status(410).build();
    }

    @PostMapping("/mpesa/callback")
    public ResponseEntity<Map<String, String>> receiveMpesaCallback(
            @RequestBody MpesaCallbackRequest callbackRequest,
            HttpServletRequest servletRequest) {
        String remoteIp = servletRequest.getHeader("X-Forwarded-For");
        if (remoteIp == null || remoteIp.isBlank()) {
            remoteIp = servletRequest.getRemoteAddr();
        }
        log.info("Received M-Pesa callback from IP: {}", remoteIp);
        paymentService.handleMpesaCallback(callbackRequest, remoteIp);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * Webhook endpoint for M-Pesa B2C transaction result (worker payouts)
     * Called by Safaricom when B2C transaction completes
     */
    @PostMapping("/mpesa/result")
    public ResponseEntity<Map<String, String>> handleMpesaB2cResult(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String remoteIp = request.getHeader("X-Forwarded-For");
        if (remoteIp == null || remoteIp.isBlank()) {
            remoteIp = request.getRemoteAddr();
        }
        log.info("MPESA B2C result received from {}: {}", remoteIp, payload);
        
        // Extract transaction details from M-Pesa B2C result payload
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) payload.get("Result");
        if (result != null) {
            String transactionId = (String) result.get("TransactionID");
            String resultCode = String.valueOf(result.get("ResultCode"));
            String conversationId = (String) result.get("ConversationID");
            
            // Log successful B2C transaction with conversation ID for tracking
            log.info("B2C transaction {} (conversation: {}) completed with code {}", 
                transactionId, conversationId, resultCode);
            
            try {
                // Process B2C payout confirmation
                paymentService.processMpesaB2cResult(transactionId, conversationId, resultCode, remoteIp);
            } catch (Exception e) {
                log.error("Error processing B2C result: {}", e.getMessage(), e);
            }
        }
        
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * Webhook endpoint for M-Pesa B2C transaction timeout
     * Called by Safaricom when B2C transaction times out
     */
    @PostMapping("/mpesa/timeout")
    public ResponseEntity<Map<String, String>> handleMpesaB2cTimeout(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String remoteIp = request.getHeader("X-Forwarded-For");
        if (remoteIp == null || remoteIp.isBlank()) {
            remoteIp = request.getRemoteAddr();
        }
        log.warn("MPESA B2C timeout received from {}: {}", remoteIp, payload);
        
        // Extract timeout details from M-Pesa B2C timeout payload
        String conversationId = (String) payload.get("ConversationID");
        String responseCode = (String) payload.get("ResponseCode");
        String responseDescription = (String) payload.get("ResponseDescription");
        
        // Log B2C timeout for investigation
        log.error("B2C transaction {} timed out with code {}: {}", 
            conversationId, responseCode, responseDescription);
        
        try {
            // Process B2C timeout - update audit log and notify admin
            paymentService.processMpesaB2cTimeout(conversationId, responseCode, responseDescription, remoteIp);
        } catch (Exception e) {
            log.error("Error processing B2C timeout: {}", e.getMessage(), e);
        }
        
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}