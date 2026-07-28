package com.kazikonnect.backend.features.platformwallet;

import com.kazikonnect.backend.features.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/platform-wallet")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Admin')")
@SuppressWarnings("null")
public class PlatformWalletController {

    private final PlatformWalletService platformWalletService;

    @GetMapping("/summary")
    public ResponseEntity<PlatformRevenueSummaryDTO> getRevenueSummary() {
        PlatformRevenueSummaryDTO summary = platformWalletService.getRevenueSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/ledger")
    public ResponseEntity<List<PlatformWalletLedger>> getLedgerEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PlatformWalletLedger> entries = platformWalletService.getLedgerEntries(page, size);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<List<PlatformWithdrawal>> getWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PlatformWithdrawal> withdrawals = platformWalletService.getWithdrawals(page, size);
        return ResponseEntity.ok(withdrawals);
    }

    @PostMapping("/withdrawals/initiate")
    public ResponseEntity<Map<String, Object>> initiateWithdrawal(
            @RequestBody WithdrawalRequest request,
            Principal principal) {
        User admin = (User) ((org.springframework.security.core.Authentication) principal).getPrincipal();
        
        PlatformWithdrawal withdrawal = platformWalletService.initiateWithdrawal(
                admin,
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

    @PostMapping("/withdrawals/{withdrawalId}/process")
    public ResponseEntity<Map<String, Object>> processWithdrawal(
            @PathVariable UUID withdrawalId,
            @RequestBody ProcessWithdrawalRequest request) {
        platformWalletService.processWithdrawal(
                withdrawalId,
                request.getReceiptNumber(),
                request.getMpesaConversationId(),
                request.getMpesaOriginatorConversationId(),
                request.getMpesaTransactionId()
        );
        
        return ResponseEntity.ok(Map.of(
                "status", "WITHDRAWAL_PROCESSED",
                "withdrawalId", withdrawalId
        ));
    }

    @PostMapping("/withdrawals/{withdrawalId}/fail")
    public ResponseEntity<Map<String, Object>> failWithdrawal(
            @PathVariable UUID withdrawalId,
            @RequestBody FailWithdrawalRequest request) {
        platformWalletService.failWithdrawal(withdrawalId, request.getFailureReason());
        
        return ResponseEntity.ok(Map.of(
                "status", "WITHDRAWAL_FAILED",
                "withdrawalId", withdrawalId
        ));
    }

    @PostMapping("/withdrawals/{withdrawalId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelWithdrawal(
            @PathVariable UUID withdrawalId,
            Principal principal) {
        User admin = (User) ((org.springframework.security.core.Authentication) principal).getPrincipal();
        platformWalletService.cancelWithdrawal(withdrawalId, admin);
        
        return ResponseEntity.ok(Map.of(
                "status", "WITHDRAWAL_CANCELLED",
                "withdrawalId", withdrawalId
        ));
    }

    @GetMapping("/wallet")
    public ResponseEntity<PlatformWallet> getPlatformWallet() {
        PlatformWallet wallet = platformWalletService.getPlatformWallet();
        return ResponseEntity.ok(wallet);
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

    public static class ProcessWithdrawalRequest {
        private String receiptNumber;
        private String mpesaConversationId;
        private String mpesaOriginatorConversationId;
        private String mpesaTransactionId;

        public String getReceiptNumber() { return receiptNumber; }
        public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
        public String getMpesaConversationId() { return mpesaConversationId; }
        public void setMpesaConversationId(String mpesaConversationId) { this.mpesaConversationId = mpesaConversationId; }
        public String getMpesaOriginatorConversationId() { return mpesaOriginatorConversationId; }
        public void setMpesaOriginatorConversationId(String mpesaOriginatorConversationId) { this.mpesaOriginatorConversationId = mpesaOriginatorConversationId; }
        public String getMpesaTransactionId() { return mpesaTransactionId; }
        public void setMpesaTransactionId(String mpesaTransactionId) { this.mpesaTransactionId = mpesaTransactionId; }
    }

    public static class FailWithdrawalRequest {
        private String failureReason;

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
}
