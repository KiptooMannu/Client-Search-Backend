package com.kazikonnect.backend.features.analytics;

import com.kazikonnect.backend.features.payment.EscrowPayment;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import com.kazikonnect.backend.features.payment.EscrowPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformFeeWithdrawalService {

    private final PlatformFeeWithdrawalRepository withdrawalRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;

    public Map<String, Object> getPlatformFeeBalance() {
        // Calculate total platform fees from released payments
        List<EscrowPayment> completedPayments = escrowPaymentRepository.findByStatus(EscrowPaymentStatus.RELEASED);

        double totalFeesCollected = completedPayments.stream()
                .mapToDouble(p -> p.getPlatformFee() != null ? p.getPlatformFee() : 0)
                .sum();
        
        double withdrawnAmount = withdrawalRepository.sumAmountByStatus(WithdrawalStatus.COMPLETED) != null 
                ? withdrawalRepository.sumAmountByStatus(WithdrawalStatus.COMPLETED) 
                : 0.0;
        
        double pendingWithdrawals = withdrawalRepository.sumAmountByStatus(WithdrawalStatus.PENDING) != null
                ? withdrawalRepository.sumAmountByStatus(WithdrawalStatus.PENDING)
                : 0.0;
        
        double processingWithdrawals = withdrawalRepository.sumAmountByStatus(WithdrawalStatus.PROCESSING) != null
                ? withdrawalRepository.sumAmountByStatus(WithdrawalStatus.PROCESSING)
                : 0.0;
        
        double availableBalance = totalFeesCollected - withdrawnAmount - pendingWithdrawals - processingWithdrawals;

        Map<String, Object> balance = new HashMap<>();
        balance.put("totalFeesCollected", totalFeesCollected);
        balance.put("withdrawnAmount", withdrawnAmount);
        balance.put("pendingWithdrawals", pendingWithdrawals);
        balance.put("processingWithdrawals", processingWithdrawals);
        balance.put("availableBalance", availableBalance);
        
        return balance;
    }

    @Transactional
    public PlatformFeeWithdrawal requestWithdrawal(WithdrawalRequest request) {
        Map<String, Object> balance = getPlatformFeeBalance();
        double availableBalance = (Double) balance.get("availableBalance");
        
        if (request.getAmount() > availableBalance) {
            throw new IllegalArgumentException("Insufficient platform fee balance");
        }

        PlatformFeeWithdrawal withdrawal = PlatformFeeWithdrawal.builder()
                .amount(request.getAmount())
                .status(WithdrawalStatus.PENDING)
                .withdrawalMethod(request.getWithdrawalMethod())
                .withdrawalDetails(request.getWithdrawalDetails())
                .mpesaPhoneNumber(request.getMpesaPhoneNumber())
                .requestedBy("ADMIN") // This should come from authenticated user
                .requestedAt(LocalDateTime.now())
                .build();

        return withdrawalRepository.save(withdrawal);
    }

    public List<PlatformFeeWithdrawal> getWithdrawalHistory(int page, int size) {
        // Simple pagination - in production, use Pageable
        List<PlatformFeeWithdrawal> all = withdrawalRepository.findAll();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, all.size());
        
        if (fromIndex >= all.size()) {
            return List.of();
        }
        
        return all.subList(fromIndex, toIndex);
    }

    public PlatformFeeWithdrawal cancelWithdrawal(UUID id) {
        PlatformFeeWithdrawal withdrawal = withdrawalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));
        
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new IllegalStateException("Can only cancel pending withdrawals");
        }
        
        withdrawal.setStatus(WithdrawalStatus.CANCELLED);
        withdrawal.setProcessedBy("ADMIN"); // Should come from authenticated user
        withdrawal.setProcessedAt(LocalDateTime.now());
        
        return withdrawalRepository.save(withdrawal);
    }

    public java.util.Optional<PlatformFeeWithdrawal> getWithdrawalById(UUID id) {
        return withdrawalRepository.findById(id);
    }
}
