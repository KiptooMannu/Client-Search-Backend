package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.wallet.WithdrawalRequest;
import com.kazikonnect.backend.features.wallet.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment Retry Scheduler
 * Handles automatic retry of failed B2C payouts with exponential backoff
 */
@Service
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final EscrowPaymentRepository escrowPaymentRepository;
    private final B2cPayoutService b2cPayoutService;
    private final PaymentService paymentService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRetryScheduler.class);

    /**
     * Retry B2C payouts that are ready for retry
     * Runs every 1 minute to check for payments ready to retry
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    @Transactional
    public void retryB2cPayouts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            List<EscrowPayment> readyForRetry = escrowPaymentRepository.findByStatusAndB2cNextRetryAtBefore(
                EscrowPaymentStatus.B2C_RETRY_PENDING, now);

            if (!readyForRetry.isEmpty()) {
                LOGGER.info("Found {} B2C payments ready for retry", readyForRetry.size());
            }

            for (EscrowPayment payment : readyForRetry) {
                try {
                    LOGGER.info("Retrying B2C payout for job {} (attempt {})", 
                        payment.getJobRequest().getId(), payment.getB2cRetryCount());
                    b2cPayoutService.retryB2cPayout(payment);
                } catch (Exception e) {
                    LOGGER.error("Failed to retry B2C payout for job {}: {}", 
                        payment.getJobRequest().getId(), e.getMessage(), e);
                }
            }

            List<WithdrawalRequest> withdrawalsReadyForRetry = withdrawalRequestRepository.findByStatusAndB2cNextRetryAtBefore(
                "B2C_RETRY_PENDING", now);

            if (!withdrawalsReadyForRetry.isEmpty()) {
                LOGGER.info("Found {} B2C withdrawals ready for retry", withdrawalsReadyForRetry.size());
            }

            for (WithdrawalRequest withdrawal : withdrawalsReadyForRetry) {
                try {
                    LOGGER.info("Retrying B2C withdrawal payout {} (attempt {})", 
                        withdrawal.getId(), withdrawal.getB2cRetryCount());
                    b2cPayoutService.retryWithdrawalPayout(withdrawal);
                } catch (Exception e) {
                    LOGGER.error("Failed to retry B2C payout for withdrawal {}: {}", 
                        withdrawal.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in B2C retry scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Query Safaricom for missed STK callbacks every 2 minutes.
     */
    @Scheduled(fixedDelay = 120000, initialDelay = 30000)
    public void reconcileMissedStkCallbacks() {
        try {
            paymentService.reconcilePendingStkPayments();
        } catch (Exception e) {
            LOGGER.error("Error in STK reconciliation scheduler: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldPayments() {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long releasedCount = escrowPaymentRepository.countByStatusAndUpdatedAtBefore(
                EscrowPaymentStatus.RELEASED, thirtyDaysAgo);
            long failedCount = escrowPaymentRepository.countByStatusAndUpdatedAtBefore(
                EscrowPaymentStatus.FAILED, thirtyDaysAgo);
            if (releasedCount > 0 || failedCount > 0) {
                LOGGER.info("Cleanup job: {} released payments, {} failed payments ready for archival", 
                    releasedCount, failedCount);
            }
        } catch (Exception e) {
            LOGGER.error("Error in payment cleanup scheduler: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    @Transactional
    public void monitorMaxRetriesExceeded() {
        try {
            List<EscrowPayment> maxRetriesExceeded = 
                escrowPaymentRepository.findByStatus(EscrowPaymentStatus.B2C_MAX_RETRIES_EXCEEDED);
            if (!maxRetriesExceeded.isEmpty()) {
                LOGGER.warn("Found {} B2C payments with max retries exceeded - requires manual intervention", 
                    maxRetriesExceeded.size());
            }
        } catch (Exception e) {
            LOGGER.error("Error monitoring max retries: {}", e.getMessage(), e);
        }
    }

    /**
     * Auto-refund PENDING payments that timed out without M-Pesa transaction evidence.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 20000)
    public void refundExpiredPendingPayments() {
        try {
            paymentService.refundExpiredPendingPayments();
        } catch (Exception e) {
            LOGGER.error("Error in expired payment refund scheduler: {}", e.getMessage(), e);
        }
    }
}
