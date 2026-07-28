package com.kazikonnect.backend.features.platformwallet;

import com.kazikonnect.backend.features.auth.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PlatformWalletService {

    private final PlatformWalletRepository platformWalletRepository;
    private final PlatformWalletLedgerRepository ledgerRepository;
    private final PlatformWithdrawalRepository withdrawalRepository;

    @Transactional(readOnly = true)
    public PlatformWallet getPlatformWallet() {
        return platformWalletRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createPlatformWallet);
    }

    @Transactional
    public PlatformWallet createPlatformWallet() {
        if (platformWalletRepository.findFirstByOrderByIdAsc().isPresent()) {
            throw new IllegalStateException("Platform wallet already exists");
        }
        PlatformWallet wallet = PlatformWallet.builder()
                .availableBalance(0.0)
                .pendingBalance(0.0)
                .totalRevenue(0.0)
                .totalWithdrawn(0.0)
                .totalTransactions(0L)
                .build();
        return platformWalletRepository.save(wallet);
    }

    @Transactional
    public void creditPlatformRevenue(UUID bookingId, UUID escrowId, User client, User worker,
                                      Double totalJobAmount, Double platformFeePercent, Double platformFeeAmount,
                                      Double workerPayout, String description) {
        PlatformWallet wallet = getPlatformWallet();
        double balanceBefore = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;
        double totalRevenue = wallet.getTotalRevenue() != null ? wallet.getTotalRevenue() : 0.0;
        long totalTransactions = wallet.getTotalTransactions() != null ? wallet.getTotalTransactions() : 0L;

        wallet.setAvailableBalance(balanceBefore + platformFeeAmount);
        wallet.setTotalRevenue(totalRevenue + platformFeeAmount);
        wallet.setTotalTransactions(totalTransactions + 1);
        platformWalletRepository.save(wallet);

        String transactionReference = generateTransactionReference("REV");
        PlatformWalletLedger ledgerEntry = PlatformWalletLedger.builder()
                .transactionReference(transactionReference)
                .bookingId(bookingId)
                .escrowId(escrowId)
                .client(client)
                .worker(worker)
                .totalJobAmount(totalJobAmount)
                .platformFeePercent(platformFeePercent)
                .platformFeeAmount(platformFeeAmount)
                .workerPayout(workerPayout)
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getAvailableBalance())
                .transactionType(PlatformTransactionType.ESCROW_RELEASE)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();
        ledgerRepository.save(ledgerEntry);

        log.info("Platform revenue credited: KES {} for booking {}", platformFeeAmount, bookingId);
    }

    @Transactional
    public PlatformWithdrawal initiateWithdrawal(User requestedBy, Double amount, WithdrawalMethod method,
                                                 String phoneNumber, String accountName, String accountNumber,
                                                 String bankName, String bankBranch, String notes) {
        PlatformWallet wallet = getPlatformWallet();
        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero");
        }
        if (amount > availableBalance) {
            throw new IllegalArgumentException("Insufficient available balance. Available: KES " + availableBalance);
        }

        String withdrawalReference = generateTransactionReference("WTH");
        PlatformWithdrawal withdrawal = PlatformWithdrawal.builder()
                .withdrawalReference(withdrawalReference)
                .requestedBy(requestedBy)
                .withdrawalMethod(method)
                .amount(amount)
                .phoneNumber(phoneNumber)
                .accountName(accountName)
                .accountNumber(accountNumber)
                .bankName(bankName)
                .bankBranch(bankBranch)
                .notes(notes)
                .status(WithdrawalStatus.PENDING)
                .build();
        withdrawalRepository.save(withdrawal);

        wallet.setAvailableBalance(availableBalance - amount);
        double pendingBalance = wallet.getPendingBalance() != null ? wallet.getPendingBalance() : 0.0;
        wallet.setPendingBalance(pendingBalance + amount);
        platformWalletRepository.save(wallet);

        log.info("Platform withdrawal initiated: KES {} by user {}", amount, requestedBy.getId());
        return withdrawal;
    }

    @Transactional
    public void processWithdrawal(UUID withdrawalId, String receiptNumber, String mpesaConversationId,
                                   String mpesaOriginatorConversationId, String mpesaTransactionId) {
        PlatformWithdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING && withdrawal.getStatus() != WithdrawalStatus.PROCESSING) {
            throw new IllegalStateException("Withdrawal is not in a processable state: " + withdrawal.getStatus());
        }

        PlatformWallet wallet = getPlatformWallet();
        double pendingBalance = wallet.getPendingBalance() != null ? wallet.getPendingBalance() : 0.0;
        double totalWithdrawn = wallet.getTotalWithdrawn() != null ? wallet.getTotalWithdrawn() : 0.0;

        withdrawal.setStatus(WithdrawalStatus.SUCCESSFUL);
        withdrawal.setReceiptNumber(receiptNumber);
        withdrawal.setMpesaConversationId(mpesaConversationId);
        withdrawal.setMpesaOriginatorConversationId(mpesaOriginatorConversationId);
        withdrawal.setMpesaTransactionId(mpesaTransactionId);
        withdrawal.setMpesaCompletedAt(LocalDateTime.now());
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        wallet.setPendingBalance(pendingBalance - withdrawal.getAmount());
        wallet.setTotalWithdrawn(totalWithdrawn + withdrawal.getAmount());
        platformWalletRepository.save(wallet);

        String transactionReference = generateTransactionReference("WTH");
        PlatformWalletLedger ledgerEntry = PlatformWalletLedger.builder()
                .transactionReference(transactionReference)
                .balanceBefore(wallet.getAvailableBalance() + withdrawal.getAmount())
                .balanceAfter(wallet.getAvailableBalance())
                .transactionType(PlatformTransactionType.WITHDRAWAL)
                .description("Platform withdrawal: " + withdrawal.getWithdrawalReference())
                .timestamp(LocalDateTime.now())
                .build();
        ledgerRepository.save(ledgerEntry);

        log.info("Platform withdrawal processed successfully: {}", withdrawal.getWithdrawalReference());
    }

    @Transactional
    public void failWithdrawal(UUID withdrawalId, String failureReason) {
        PlatformWithdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING && withdrawal.getStatus() != WithdrawalStatus.PROCESSING) {
            throw new IllegalStateException("Withdrawal is not in a failable state: " + withdrawal.getStatus());
        }

        PlatformWallet wallet = getPlatformWallet();
        double pendingBalance = wallet.getPendingBalance() != null ? wallet.getPendingBalance() : 0.0;
        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setFailureReason(failureReason);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        wallet.setPendingBalance(pendingBalance - withdrawal.getAmount());
        wallet.setAvailableBalance(availableBalance + withdrawal.getAmount());
        platformWalletRepository.save(wallet);

        String transactionReference = generateTransactionReference("WTH");
        PlatformWalletLedger ledgerEntry = PlatformWalletLedger.builder()
                .transactionReference(transactionReference)
                .balanceBefore(availableBalance)
                .balanceAfter(wallet.getAvailableBalance())
                .transactionType(PlatformTransactionType.WITHDRAWAL_FAILED)
                .description("Failed platform withdrawal: " + withdrawal.getWithdrawalReference() + " - " + failureReason)
                .timestamp(LocalDateTime.now())
                .build();
        ledgerRepository.save(ledgerEntry);

        log.info("Platform withdrawal failed: {} - {}", withdrawal.getWithdrawalReference(), failureReason);
    }

    @Transactional
    public void cancelWithdrawal(UUID withdrawalId, User cancelledBy) {
        PlatformWithdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Only pending withdrawals can be cancelled");
        }

        PlatformWallet wallet = getPlatformWallet();
        double pendingBalance = wallet.getPendingBalance() != null ? wallet.getPendingBalance() : 0.0;
        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        withdrawal.setStatus(WithdrawalStatus.CANCELLED);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        wallet.setPendingBalance(pendingBalance - withdrawal.getAmount());
        wallet.setAvailableBalance(availableBalance + withdrawal.getAmount());
        platformWalletRepository.save(wallet);

        String transactionReference = generateTransactionReference("WTH");
        PlatformWalletLedger ledgerEntry = PlatformWalletLedger.builder()
                .transactionReference(transactionReference)
                .balanceBefore(availableBalance)
                .balanceAfter(wallet.getAvailableBalance())
                .transactionType(PlatformTransactionType.WITHDRAWAL_CANCELLED)
                .description("Cancelled platform withdrawal: " + withdrawal.getWithdrawalReference())
                .timestamp(LocalDateTime.now())
                .build();
        ledgerRepository.save(ledgerEntry);

        log.info("Platform withdrawal cancelled: {} by user {}", withdrawal.getWithdrawalReference(), cancelledBy.getId());
    }

    @Transactional(readOnly = true)
    public PlatformRevenueSummaryDTO getRevenueSummary() {
        PlatformWallet wallet = getPlatformWallet();
        LocalDateTime now = LocalDateTime.now();
        
        Double revenueToday = ledgerRepository.sumRevenueByDateRange(
                now.truncatedTo(ChronoUnit.DAYS), now);
        Double revenueThisWeek = ledgerRepository.sumRevenueByDateRange(
                now.minusDays(7), now);
        Double revenueThisMonth = ledgerRepository.sumRevenueByDateRange(
                now.minusDays(30), now);
        Double revenueThisYear = ledgerRepository.sumRevenueByDateRange(
                now.minusDays(365), now);

        Long transactionsToday = ledgerRepository.countTransactionsByDateRange(
                now.truncatedTo(ChronoUnit.DAYS), now);
        Long transactionsThisWeek = ledgerRepository.countTransactionsByDateRange(
                now.minusDays(7), now);
        Long transactionsThisMonth = ledgerRepository.countTransactionsByDateRange(
                now.minusDays(30), now);
        Long transactionsThisYear = ledgerRepository.countTransactionsByDateRange(
                now.minusDays(365), now);

        Double avgFeePerTransaction = transactionsToday != null && transactionsToday > 0
                ? (revenueToday != null ? revenueToday / transactionsToday : 0.0)
                : 0.0;

        return PlatformRevenueSummaryDTO.builder()
                .totalRevenueEarned(wallet.getTotalRevenue() != null ? wallet.getTotalRevenue() : 0.0)
                .availableBalance(wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0)
                .pendingBalance(wallet.getPendingBalance() != null ? wallet.getPendingBalance() : 0.0)
                .totalWithdrawn(wallet.getTotalWithdrawn() != null ? wallet.getTotalWithdrawn() : 0.0)
                .revenueToday(revenueToday != null ? revenueToday : 0.0)
                .revenueThisWeek(revenueThisWeek != null ? revenueThisWeek : 0.0)
                .revenueThisMonth(revenueThisMonth != null ? revenueThisMonth : 0.0)
                .revenueThisYear(revenueThisYear != null ? revenueThisYear : 0.0)
                .totalCompletedTransactions(wallet.getTotalTransactions() != null ? wallet.getTotalTransactions() : 0L)
                .transactionsToday(transactionsToday != null ? transactionsToday : 0L)
                .transactionsThisWeek(transactionsThisWeek != null ? transactionsThisWeek : 0L)
                .transactionsThisMonth(transactionsThisMonth != null ? transactionsThisMonth : 0L)
                .transactionsThisYear(transactionsThisYear != null ? transactionsThisYear : 0L)
                .averagePlatformFeePerTransaction(avgFeePerTransaction)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PlatformWalletLedger> getLedgerEntries(int page, int size) {
        return ledgerRepository.findAllOrderByTimestampDesc(
                org.springframework.data.domain.PageRequest.of(page, size)
        ).getContent();
    }

    @Transactional(readOnly = true)
    public List<PlatformWithdrawal> getWithdrawals(int page, int size) {
        return withdrawalRepository.findAllOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, size)
        ).getContent();
    }

    private String generateTransactionReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
