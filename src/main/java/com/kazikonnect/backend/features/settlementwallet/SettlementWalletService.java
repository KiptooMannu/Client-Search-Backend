package com.kazikonnect.backend.features.settlementwallet;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.platformwallet.WithdrawalMethod;
import com.kazikonnect.backend.features.platformwallet.WithdrawalStatus;
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
public class SettlementWalletService {

    private final ClientSettlementWalletRepository walletRepository;
    private final SettlementWalletTransactionRepository transactionRepository;
    private final SettlementWithdrawalRepository withdrawalRepository;

    @Transactional(readOnly = true)
    public ClientSettlementWallet getWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> createWallet(user));
    }

    @Transactional
    public ClientSettlementWallet createWallet(User user) {
        if (walletRepository.existsByUserId(user.getId())) {
            throw new IllegalStateException("Settlement wallet already exists for user");
        }
        ClientSettlementWallet wallet = ClientSettlementWallet.builder()
                .user(user)
                .availableBalance(0.0)
                .pendingCredits(0.0)
                .totalRefunded(0.0)
                .totalWithdrawn(0.0)
                .totalSettlementCredits(0.0)
                .isFrozen(false)
                .build();
        return walletRepository.save(wallet);
    }

    @Transactional
    public void creditWallet(User user, Double amount, SettlementTransactionType transactionType,
                            UUID bookingId, UUID escrowId, String description) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be greater than zero");
        }

        ClientSettlementWallet wallet = getWallet(user);
        if (wallet.getIsFrozen()) {
            throw new IllegalStateException("Wallet is frozen and cannot receive credits");
        }

        double balanceBefore = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;
        double totalRefunded = wallet.getTotalRefunded() != null ? wallet.getTotalRefunded() : 0.0;
        double totalSettlementCredits = wallet.getTotalSettlementCredits() != null ? wallet.getTotalSettlementCredits() : 0.0;

        wallet.setAvailableBalance(balanceBefore + amount);
        wallet.setTotalRefunded(totalRefunded + amount);
        wallet.setTotalSettlementCredits(totalSettlementCredits + amount);
        walletRepository.save(wallet);

        String transactionReference = generateTransactionReference("SET");
        SettlementWalletTransaction transaction = SettlementWalletTransaction.builder()
                .wallet(wallet)
                .transactionReference(transactionReference)
                .bookingId(bookingId)
                .escrowId(escrowId)
                .transactionType(transactionType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getAvailableBalance())
                .description(description)
                .status(TransactionStatus.COMPLETED)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        log.info("Settlement wallet credited: KES {} for user {} (type: {})", amount, user.getId(), transactionType);
    }

    @Transactional
    public void debitWallet(User user, Double amount, UUID referenceId, String description) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be greater than zero");
        }

        ClientSettlementWallet wallet = getWallet(user);
        if (wallet.getIsFrozen()) {
            throw new IllegalStateException("Wallet is frozen and cannot process debits");
        }

        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;
        if (amount > availableBalance) {
            throw new IllegalArgumentException("Insufficient settlement wallet balance. Available: KES " + availableBalance);
        }

        double totalWithdrawn = wallet.getTotalWithdrawn() != null ? wallet.getTotalWithdrawn() : 0.0;

        wallet.setAvailableBalance(availableBalance - amount);
        wallet.setTotalWithdrawn(totalWithdrawn + amount);
        walletRepository.save(wallet);

        String transactionReference = generateTransactionReference("SET");
        SettlementWalletTransaction transaction = SettlementWalletTransaction.builder()
                .wallet(wallet)
                .transactionReference(transactionReference)
                .bookingId(referenceId)
                .transactionType(SettlementTransactionType.WITHDRAWAL)
                .amount(amount)
                .balanceBefore(availableBalance)
                .balanceAfter(wallet.getAvailableBalance())
                .description(description)
                .status(TransactionStatus.COMPLETED)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        log.info("Settlement wallet debited: KES {} for user {}", amount, user.getId());
    }

    @Transactional
    public SettlementWithdrawal initiateWithdrawal(User user, Double amount, WithdrawalMethod method,
                                                   String phoneNumber, String accountName, String accountNumber,
                                                   String bankName, String bankBranch, String notes) {
        ClientSettlementWallet wallet = getWallet(user);
        if (wallet.getIsFrozen()) {
            throw new IllegalStateException("Wallet is frozen and cannot process withdrawals");
        }

        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero");
        }
        if (amount > availableBalance) {
            throw new IllegalArgumentException("Insufficient available balance. Available: KES " + availableBalance);
        }

        String withdrawalReference = generateTransactionReference("SWD");
        SettlementWithdrawal withdrawal = SettlementWithdrawal.builder()
                .wallet(wallet)
                .requestedBy(user)
                .withdrawalReference(withdrawalReference)
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
        double pendingCredits = wallet.getPendingCredits() != null ? wallet.getPendingCredits() : 0.0;
        wallet.setPendingCredits(pendingCredits + amount);
        walletRepository.save(wallet);

        log.info("Settlement withdrawal initiated: KES {} for user {}", amount, user.getId());
        return withdrawal;
    }

    @Transactional
    public void processWithdrawal(UUID withdrawalId, String receiptNumber, String mpesaConversationId,
                                   String mpesaOriginatorConversationId, String mpesaTransactionId) {
        SettlementWithdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING && withdrawal.getStatus() != WithdrawalStatus.PROCESSING) {
            throw new IllegalStateException("Withdrawal is not in a processable state: " + withdrawal.getStatus());
        }

        ClientSettlementWallet wallet = withdrawal.getWallet();
        double pendingCredits = wallet.getPendingCredits() != null ? wallet.getPendingCredits() : 0.0;

        withdrawal.setStatus(WithdrawalStatus.SUCCESSFUL);
        withdrawal.setReceiptNumber(receiptNumber);
        withdrawal.setMpesaConversationId(mpesaConversationId);
        withdrawal.setMpesaOriginatorConversationId(mpesaOriginatorConversationId);
        withdrawal.setMpesaTransactionId(mpesaTransactionId);
        withdrawal.setMpesaCompletedAt(LocalDateTime.now());
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        wallet.setPendingCredits(pendingCredits - withdrawal.getAmount());
        walletRepository.save(wallet);

        debitWallet(wallet.getUser(), withdrawal.getAmount(), withdrawal.getId(),
                "Settlement withdrawal processed: " + withdrawal.getWithdrawalReference());

        log.info("Settlement withdrawal processed successfully: {}", withdrawal.getWithdrawalReference());
    }

    @Transactional
    public void failWithdrawal(UUID withdrawalId, String failureReason) {
        SettlementWithdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING && withdrawal.getStatus() != WithdrawalStatus.PROCESSING) {
            throw new IllegalStateException("Withdrawal is not in a failable state: " + withdrawal.getStatus());
        }

        ClientSettlementWallet wallet = withdrawal.getWallet();
        double pendingCredits = wallet.getPendingCredits() != null ? wallet.getPendingCredits() : 0.0;
        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setFailureReason(failureReason);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        wallet.setPendingCredits(pendingCredits - withdrawal.getAmount());
        wallet.setAvailableBalance(availableBalance + withdrawal.getAmount());
        walletRepository.save(wallet);

        String transactionReference = generateTransactionReference("SET");
        SettlementWalletTransaction transaction = SettlementWalletTransaction.builder()
                .wallet(wallet)
                .transactionReference(transactionReference)
                .bookingId(withdrawal.getId())
                .transactionType(SettlementTransactionType.WITHDRAWAL_FAILED)
                .amount(withdrawal.getAmount())
                .balanceBefore(availableBalance)
                .balanceAfter(wallet.getAvailableBalance())
                .description("Failed settlement withdrawal: " + withdrawal.getWithdrawalReference() + " - " + failureReason)
                .status(TransactionStatus.COMPLETED)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        log.info("Settlement withdrawal failed: {} - {}", withdrawal.getWithdrawalReference(), failureReason);
    }

    @Transactional
    public void cancelWithdrawal(UUID withdrawalId, User cancelledBy) {
        SettlementWithdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Only pending withdrawals can be cancelled");
        }

        ClientSettlementWallet wallet = withdrawal.getWallet();
        double pendingCredits = wallet.getPendingCredits() != null ? wallet.getPendingCredits() : 0.0;
        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        withdrawal.setStatus(WithdrawalStatus.CANCELLED);
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);

        wallet.setPendingCredits(pendingCredits - withdrawal.getAmount());
        wallet.setAvailableBalance(availableBalance + withdrawal.getAmount());
        walletRepository.save(wallet);

        String transactionReference = generateTransactionReference("SET");
        SettlementWalletTransaction transaction = SettlementWalletTransaction.builder()
                .wallet(wallet)
                .transactionReference(transactionReference)
                .bookingId(withdrawal.getId())
                .transactionType(SettlementTransactionType.WITHDRAWAL_CANCELLED)
                .amount(withdrawal.getAmount())
                .balanceBefore(availableBalance)
                .balanceAfter(wallet.getAvailableBalance())
                .description("Cancelled settlement withdrawal: " + withdrawal.getWithdrawalReference())
                .status(TransactionStatus.COMPLETED)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        log.info("Settlement withdrawal cancelled: {} by user {}", withdrawal.getWithdrawalReference(), cancelledBy.getId());
    }

    @Transactional
    public void freezeWallet(UUID walletId, String reason, User frozenBy) {
        ClientSettlementWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        wallet.setIsFrozen(true);
        wallet.setFreezeReason(reason);
        wallet.setFrozenAt(LocalDateTime.now());
        walletRepository.save(wallet);

        log.info("Settlement wallet frozen: {} by user {} - Reason: {}", walletId, frozenBy.getId(), reason);
    }

    @Transactional
    public void unfreezeWallet(UUID walletId, User unfrozenBy) {
        ClientSettlementWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        wallet.setIsFrozen(false);
        wallet.setFreezeReason(null);
        wallet.setUnfrozenAt(LocalDateTime.now());
        walletRepository.save(wallet);

        log.info("Settlement wallet unfrozen: {} by user {}", walletId, unfrozenBy.getId());
    }

    @Transactional
    public void adminCredit(User user, Double amount, String reason, User admin) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be greater than zero");
        }

        ClientSettlementWallet wallet = getWallet(user);
        double balanceBefore = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;
        double totalSettlementCredits = wallet.getTotalSettlementCredits() != null ? wallet.getTotalSettlementCredits() : 0.0;

        wallet.setAvailableBalance(balanceBefore + amount);
        wallet.setTotalSettlementCredits(totalSettlementCredits + amount);
        walletRepository.save(wallet);

        String transactionReference = generateTransactionReference("ADM");
        SettlementWalletTransaction transaction = SettlementWalletTransaction.builder()
                .wallet(wallet)
                .transactionReference(transactionReference)
                .transactionType(SettlementTransactionType.ADMIN_CREDIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getAvailableBalance())
                .description("Admin credit by " + admin.getFullName() + ": " + reason)
                .status(TransactionStatus.COMPLETED)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        log.info("Admin credit to settlement wallet: KES {} for user {} by admin {}", amount, user.getId(), admin.getId());
    }

    @Transactional
    public void adminDebit(User user, Double amount, String reason, User admin) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be greater than zero");
        }

        ClientSettlementWallet wallet = getWallet(user);
        double availableBalance = wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0;

        if (amount > availableBalance) {
            throw new IllegalArgumentException("Insufficient balance for admin debit");
        }

        wallet.setAvailableBalance(availableBalance - amount);
        walletRepository.save(wallet);

        String transactionReference = generateTransactionReference("ADM");
        SettlementWalletTransaction transaction = SettlementWalletTransaction.builder()
                .wallet(wallet)
                .transactionReference(transactionReference)
                .transactionType(SettlementTransactionType.ADMIN_DEBIT)
                .amount(amount)
                .balanceBefore(availableBalance)
                .balanceAfter(wallet.getAvailableBalance())
                .description("Admin debit by " + admin.getFullName() + ": " + reason)
                .status(TransactionStatus.COMPLETED)
                .timestamp(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        log.info("Admin debit from settlement wallet: KES {} for user {} by admin {}", amount, user.getId(), admin.getId());
    }

    @Transactional(readOnly = true)
    public SettlementWalletSummaryDTO getWalletSummary(User user) {
        ClientSettlementWallet wallet = getWallet(user);
        LocalDateTime now = LocalDateTime.now();

        Double refundedToday = transactionRepository.sumByUserIdAndTypeAndDateRange(
                user.getId(), SettlementTransactionType.REFUND,
                now.truncatedTo(ChronoUnit.DAYS), now);
        Double refundedThisWeek = transactionRepository.sumByUserIdAndTypeAndDateRange(
                user.getId(), SettlementTransactionType.REFUND,
                now.minusDays(7), now);
        Double refundedThisMonth = transactionRepository.sumByUserIdAndTypeAndDateRange(
                user.getId(), SettlementTransactionType.REFUND,
                now.minusDays(30), now);

        Double withdrawnToday = withdrawalRepository.sumByUserIdAndStatusAndDateRange(
                user.getId(), WithdrawalStatus.SUCCESSFUL,
                now.truncatedTo(ChronoUnit.DAYS), now);
        Double withdrawnThisWeek = withdrawalRepository.sumByUserIdAndStatusAndDateRange(
                user.getId(), WithdrawalStatus.SUCCESSFUL,
                now.minusDays(7), now);
        Double withdrawnThisMonth = withdrawalRepository.sumByUserIdAndStatusAndDateRange(
                user.getId(), WithdrawalStatus.SUCCESSFUL,
                now.minusDays(30), now);

        return SettlementWalletSummaryDTO.builder()
                .availableBalance(wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0)
                .pendingCredits(wallet.getPendingCredits() != null ? wallet.getPendingCredits() : 0.0)
                .totalRefunded(wallet.getTotalRefunded() != null ? wallet.getTotalRefunded() : 0.0)
                .totalWithdrawn(wallet.getTotalWithdrawn() != null ? wallet.getTotalWithdrawn() : 0.0)
                .totalSettlementCredits(wallet.getTotalSettlementCredits() != null ? wallet.getTotalSettlementCredits() : 0.0)
                .isFrozen(wallet.getIsFrozen() != null ? wallet.getIsFrozen() : false)
                .freezeReason(wallet.getFreezeReason())
                .refundedToday(refundedToday != null ? refundedToday : 0.0)
                .refundedThisWeek(refundedThisWeek != null ? refundedThisWeek : 0.0)
                .refundedThisMonth(refundedThisMonth != null ? refundedThisMonth : 0.0)
                .withdrawnToday(withdrawnToday != null ? withdrawnToday : 0.0)
                .withdrawnThisWeek(withdrawnThisWeek != null ? withdrawnThisWeek : 0.0)
                .withdrawnThisMonth(withdrawnThisMonth != null ? withdrawnThisMonth : 0.0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SettlementWalletTransaction> getTransactions(User user, int page, int size) {
        ClientSettlementWallet wallet = getWallet(user);
        return transactionRepository.findByWalletIdOrderByTimestampDesc(
                wallet.getId(), org.springframework.data.domain.PageRequest.of(page, size)
        ).getContent();
    }

    @Transactional(readOnly = true)
    public List<SettlementWithdrawal> getWithdrawals(User user, int page, int size) {
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(page, size)
        ).getContent();
    }

    @Transactional(readOnly = true)
    public List<ClientSettlementWallet> getAllWallets() {
        return walletRepository.findAll();
    }

    private String generateTransactionReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
