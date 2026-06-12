package com.kazikonnect.backend.features.wallet;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.kazikonnect.backend.features.payment.B2cPayoutService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WalletController {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final B2cPayoutService b2cPayoutService;

    @GetMapping("/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getBalance(Principal principal) {
        // Validate principal
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        // Find user or throw exception
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        double balance = walletService.getBalance(user);
        return ResponseEntity.ok(Map.of("balance", balance));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAuthority('Worker')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> withdraw(
            @RequestBody WalletWithdrawRequest request,
            Principal principal) {
        if (request.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Withdrawal amount must be greater than zero.");
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        double currentBalance = walletService.getBalance(user);
        if (currentBalance < request.amount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient wallet balance.");
        }

        UUID referenceId = UUID.randomUUID();

        // 1. Debit the wallet immediately
        walletService.debitWallet(user, request.amount(),
                "Worker withdrawal to " + request.destinationPhoneNumber(),
                referenceId);

        // 2. Create the withdrawal request record
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .user(user)
                .amount(request.amount())
                .phoneNumber(request.destinationPhoneNumber())
                .status("PENDING")
                .build();

        withdrawal = withdrawalRequestRepository.save(withdrawal);

        // 3. Trigger B2C payout (this propagates transaction, so failure rolls back debit)
        try {
            b2cPayoutService.initiateWithdrawalPayout(withdrawal);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to initiate M-Pesa payout: " + e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Withdrawal requested successfully. Payout is being processed via M-Pesa B2C.",
                "balance", walletService.getBalance(user)
        ));
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WalletTransactionDTO>> getTransactions(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return ResponseEntity.ok(walletTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(WalletTransactionDTO::from)
                .toList());
    }

    public record WalletWithdrawRequest(double amount, String destinationPhoneNumber) {}

    public record WalletTransactionDTO(
            String id,
            String txnType,
            Double amount,
            Double balanceAfter,
            String referenceId,
            String description,
            String createdAt
    ) {
        public static WalletTransactionDTO from(WalletTransaction transaction) {
            return new WalletTransactionDTO(
                    transaction.getId().toString(),
                    transaction.getTxnType().name(),
                    transaction.getAmount(),
                    transaction.getBalanceAfter(),
                    transaction.getReferenceId() != null ? transaction.getReferenceId().toString() : null,
                    transaction.getDescription(),
                    transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : null
            );
        }
    }
}
