package com.kazikonnect.backend.features.payment;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    public record StkPushRequest(UUID jobId, String phoneNumber) {}
    public record PaymentActionRequest(UUID jobId) {}

    @PostMapping("/mpesa/stkpush")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<StkPushResponse> initiateStkPush(
            @RequestBody StkPushRequest request,
            Principal principal) {
        return ResponseEntity.ok(paymentService.initiateStkPush(request.jobId(), request.phoneNumber(), principal));
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

    @PostMapping("/escrow/release")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> releaseEscrow(@RequestBody PaymentActionRequest request, Principal principal) {
        return ResponseEntity.status(410).body(Map.of("message", "Legacy escrow operations have been removed. Use M-Pesa payment flows and job approval instead."));
    }

    @PostMapping("/escrow/refund")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> refundEscrow(@RequestBody PaymentActionRequest request, Principal principal) {
        return ResponseEntity.status(410).body(Map.of("message", "Legacy escrow operations have been removed. Refunds are handled by support."));
    }

    @PostMapping("/mpesa/callback")
    public ResponseEntity<Map<String, String>> receiveMpesaCallback(
            @RequestBody MpesaCallbackRequest callbackRequest,
            HttpServletRequest servletRequest) {
        String remoteIp = servletRequest.getHeader("X-Forwarded-For");
        if (remoteIp == null || remoteIp.isBlank()) {
            remoteIp = servletRequest.getRemoteAddr();
        }
        paymentService.handleMpesaCallback(callbackRequest, remoteIp);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
