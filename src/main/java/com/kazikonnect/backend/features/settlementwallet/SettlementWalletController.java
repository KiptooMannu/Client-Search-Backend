package com.kazikonnect.backend.features.settlementwallet;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.platformwallet.WithdrawalMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/settlement-wallet")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SettlementWalletController {

    private final SettlementWalletService settlementWalletService;
    private final UserRepository userRepository;

    @GetMapping("/summary")
    public ResponseEntity<SettlementWalletSummaryDTO> getWalletSummary(Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        SettlementWalletSummaryDTO summary = settlementWalletService.getWalletSummary(user);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<SettlementWalletTransaction>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<SettlementWalletTransaction> transactions = settlementWalletService.getTransactions(user, page, size);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<List<SettlementWithdrawal>> getWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<SettlementWithdrawal> withdrawals = settlementWalletService.getWithdrawals(user, page, size);
        return ResponseEntity.ok(withdrawals);
    }

    @PostMapping("/withdrawals/initiate")
    public ResponseEntity<Map<String, Object>> initiateWithdrawal(
            @RequestBody WithdrawalRequest request,
            Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        SettlementWithdrawal withdrawal = settlementWalletService.initiateWithdrawal(
                user,
                request.getAmount(),
                request.getMethod(),
                request.getPhoneNumber(),
                request.getAccountName(),
                request.getAccountNumber(),
                request.getBankName(),
                request.getBankBranch(),
                request.getNotes()
        );

        return ResponseEntity.ok(Map.of(
                "status", "WITHDRAWAL_INITIATED",
                "withdrawalId", withdrawal.getId(),
                "withdrawalReference", withdrawal.getWithdrawalReference(),
                "amount", withdrawal.getAmount(),
                "status", withdrawal.getStatus().toString()
        ));
    }

    @PostMapping("/withdrawals/{withdrawalId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelWithdrawal(
            @PathVariable UUID withdrawalId,
            Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        settlementWalletService.cancelWithdrawal(withdrawalId, user);

        return ResponseEntity.ok(Map.of(
                "status", "WITHDRAWAL_CANCELLED",
                "withdrawalId", withdrawalId
        ));
    }

    @GetMapping("/wallet")
    public ResponseEntity<ClientSettlementWallet> getWallet(Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        ClientSettlementWallet wallet = settlementWalletService.getWallet(user);
        return ResponseEntity.ok(wallet);
    }

    // Admin endpoints
    @GetMapping("/admin/wallets")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<List<ClientSettlementWallet>> getAllWallets() {
        List<ClientSettlementWallet> wallets = settlementWalletService.getAllWallets();
        return ResponseEntity.ok(wallets);
    }

    @PostMapping("/admin/wallets/{walletId}/freeze")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> freezeWallet(
            @PathVariable UUID walletId,
            @RequestBody FreezeWalletRequest request,
            Principal principal) {
        User admin = getAuthenticatedUser(principal);
        if (admin == null) {
            return ResponseEntity.status(401).build();
        }
        settlementWalletService.freezeWallet(walletId, request.getReason(), admin);
        
        return ResponseEntity.ok(Map.of(
                "status", "WALLET_FROZEN",
                "walletId", walletId
        ));
    }

    @PostMapping("/admin/wallets/{walletId}/unfreeze")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> unfreezeWallet(
            @PathVariable UUID walletId,
            Principal principal) {
        User admin = getAuthenticatedUser(principal);
        if (admin == null) {
            return ResponseEntity.status(401).build();
        }
        settlementWalletService.unfreezeWallet(walletId, admin);
        
        return ResponseEntity.ok(Map.of(
                "status", "WALLET_UNFROZEN",
                "walletId", walletId
        ));
    }

    @PostMapping("/admin/wallets/{userId}/credit")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> adminCredit(
            @PathVariable UUID userId,
            @RequestBody AdminAdjustmentRequest request,
            Principal principal) {
        User admin = getAuthenticatedUser(principal);
        if (admin == null) {
            return ResponseEntity.status(401).build();
        }
        User targetUser = getUserById(userId);
        settlementWalletService.adminCredit(targetUser, request.getAmount(), request.getReason(), admin);
        
        return ResponseEntity.ok(Map.of(
                "status", "CREDIT_APPLIED",
                "userId", userId,
                "amount", request.getAmount()
        ));
    }

    @PostMapping("/admin/wallets/{userId}/debit")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> adminDebit(
            @PathVariable UUID userId,
            @RequestBody AdminAdjustmentRequest request,
            Principal principal) {
        User admin = getAuthenticatedUser(principal);
        if (admin == null) {
            return ResponseEntity.status(401).build();
        }
        User targetUser = getUserById(userId);
        settlementWalletService.adminDebit(targetUser, request.getAmount(), request.getReason(), admin);
        
        return ResponseEntity.ok(Map.of(
                "status", "DEBIT_APPLIED",
                "userId", userId,
                "amount", request.getAmount()
        ));
    }

    // DTOs for requests
    public static class WithdrawalRequest {
        private Double amount;
        private WithdrawalMethod method;
        private String phoneNumber;
        private String accountName;
        private String accountNumber;
        private String bankName;
        private String bankBranch;
        private String notes;

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public WithdrawalMethod getMethod() { return method; }
        public void setMethod(WithdrawalMethod method) { this.method = method; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }
        public String getBankBranch() { return bankBranch; }
        public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class FreezeWalletRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class AdminAdjustmentRequest {
        private Double amount;
        private String reason;

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // Helper method
    private User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }
}
