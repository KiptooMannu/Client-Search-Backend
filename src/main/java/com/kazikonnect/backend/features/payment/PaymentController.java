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
}