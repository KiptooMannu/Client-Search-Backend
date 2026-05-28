package com.kazikonnect.backend.features.wallet;

import com.kazikonnect.backend.features.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class WalletServiceTests {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private WalletService walletService;

    @Captor
    private ArgumentCaptor<WalletTransaction> transactionCaptor;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void creditWalletShouldIncreaseBalanceAndRecordTransaction() {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(0.0)
                .totalEarned(0.0)
                .totalWithdrawn(0.0)
                .build();

        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        walletService.creditWallet(user, 200.0, "Escrow release");

        assertEquals(200.0, wallet.getBalance());
        assertEquals(200.0, wallet.getTotalEarned());
        verify(walletRepository).save(wallet);
        verify(walletTransactionRepository).save(transactionCaptor.capture());
        assertNotNull(transactionCaptor.getValue());
        WalletTransaction txn = transactionCaptor.getValue();
        assertEquals(WalletTransactionType.CREDIT, txn.getTxnType());
        assertEquals(200.0, txn.getAmount());
        assertEquals(200.0, txn.getBalanceAfter());
    }

    @Test
    void debitWalletShouldDecreaseBalanceAndRecordTransaction() {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(300.0)
                .totalEarned(300.0)
                .totalWithdrawn(0.0)
                .build();

        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        walletService.debitWallet(user, 100.0, "Withdrawal", UUID.randomUUID());

        assertEquals(200.0, wallet.getBalance());
        assertEquals(100.0, wallet.getTotalWithdrawn());
        verify(walletRepository).save(wallet);
        verify(walletTransactionRepository).save(transactionCaptor.capture());
        assertNotNull(transactionCaptor.getValue());
        WalletTransaction txn = transactionCaptor.getValue();
        assertEquals(WalletTransactionType.DEBIT, txn.getTxnType());
        assertEquals(100.0, txn.getAmount());
        assertEquals(200.0, txn.getBalanceAfter());
    }

    @Test
    void debitWalletShouldFailWhenBalanceIsInsufficient() {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(50.0)
                .build();

        when(walletRepository.findByUserId(user.getId())).thenReturn(Optional.of(wallet));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> walletService.debitWallet(user, 100.0, "Withdrawal", UUID.randomUUID()));
        assertEquals("Insufficient wallet balance for withdrawal.", exception.getMessage());
    }
}