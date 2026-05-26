package com.kazikonnect.backend.features.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/mpesa/stkpush")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<StkPushResponse> initiateStkPush(
            @RequestParam UUID jobId,
            @RequestParam String phoneNumber,
            Principal principal) {
        return ResponseEntity.ok(paymentService.initiateStkPush(jobId, phoneNumber, principal));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(jobId));
    }

    @PostMapping("/escrow/release")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> releaseEscrow(@RequestParam UUID jobId, Principal principal) {
        paymentService.releaseEscrow(jobId, principal);
        return ResponseEntity.ok(Map.of("message", "Escrow successfully released."));
    }

    @PostMapping("/escrow/refund")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> refundEscrow(@RequestParam UUID jobId, Principal principal) {
        paymentService.refundEscrow(jobId, principal);
        return ResponseEntity.ok(Map.of("message", "Escrow successfully refunded."));
    }

    @PostMapping("/mpesa/callback")
    public ResponseEntity<Map<String, String>> receiveMpesaCallback(@RequestBody Map<String, Object> payload) {
        paymentService.handleMpesaCallback(payload);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}