package com.kazikonnect.backend.features.wallet;

import com.kazikonnect.backend.features.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WalletService {

    private final WalletRepository walletRepository;

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
        wallet.setBalance(wallet.getBalance() + amount);
        walletRepository.save(wallet);
    }

    @Transactional
    public Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .user(user)
                        .balance(0.0)
                        .build()));
    }
}
