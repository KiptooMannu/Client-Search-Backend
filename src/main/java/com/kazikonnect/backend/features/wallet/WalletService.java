package com.kazikonnect.backend.features.wallet;

import com.kazikonnect.backend.features.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional(readOnly = true)
    public double getBalance(User user) {
        return getOrCreateWallet(user).getBalance();
    }

    @Transactional
    public void creditWallet(User user, double amount, String reason) {
        if (amount <= 0) {
            return;
        }
        Wallet wallet = getOrCreateWallet(user);
        double currentBalance = wallet.getBalance() != null ? wallet.getBalance() : 0.0;
        double currentEarned = wallet.getTotalEarned() != null ? wallet.getTotalEarned() : 0.0;
        wallet.setBalance(currentBalance + amount);
        wallet.setTotalEarned(currentEarned + amount);
        walletRepository.save(wallet);
        saveWalletTransaction(user, WalletTransactionType.CREDIT, amount, wallet.getBalance(), null, reason);
    }

    @Transactional
    public void debitWallet(User user, double amount, String description, UUID referenceId) {
        if (amount <= 0) {
            throw new RuntimeException("Withdrawal amount must be greater than zero.");
        }
        Wallet wallet = getOrCreateWallet(user);
        double currentBalance = wallet.getBalance() != null ? wallet.getBalance() : 0.0;
        if (currentBalance < amount) {
            throw new RuntimeException("Insufficient wallet balance for withdrawal.");
        }
        double currentWithdrawn = wallet.getTotalWithdrawn() != null ? wallet.getTotalWithdrawn() : 0.0;
        wallet.setBalance(currentBalance - amount);
        wallet.setTotalWithdrawn(currentWithdrawn + amount);
        walletRepository.save(wallet);
        saveWalletTransaction(user, WalletTransactionType.DEBIT, amount, wallet.getBalance(), referenceId, description);
    }

    @Transactional
    public Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .user(user)
                        .balance(0.0)
                        .totalEarned(0.0)
                        .totalWithdrawn(0.0)
                        .build()));
    }

    private void saveWalletTransaction(User user, WalletTransactionType type, double amount, double balanceAfter,
                                        UUID referenceId, String description) {
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(user)
                .txnType(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(referenceId)
                .description(description)
                .build());
    }
}
