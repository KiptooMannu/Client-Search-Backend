package com.kazikonnect.backend.features.payment;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRetryScheduler.class);

    /**
     * Retry B2C payouts that are ready for retry
     * Runs every 1 minute to check for payments ready to retry
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)  // Check every 60 seconds
    @Transactional
    public void retryB2cPayouts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find all payments ready for retry
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

        } catch (Exception e) {
            LOGGER.error("Error in B2C retry scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup completed and failed payments (archive old records)
     * Runs daily at 2 AM to clean up old payment records
     */
    @Scheduled(cron = "0 0 2 * * *")  // 2:00 AM daily
    @Transactional
    public void cleanupOldPayments() {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            // Archive released payments older than 30 days
            long releasedCount = escrowPaymentRepository.countByStatusAndUpdatedAtBefore(
                EscrowPaymentStatus.RELEASED, thirtyDaysAgo);

            // Archive failed payments older than 30 days
            long failedCount = escrowPaymentRepository.countByStatusAndUpdatedAtBefore(
                EscrowPaymentStatus.FAILED, thirtyDaysAgo);

            if (releasedCount > 0 || failedCount > 0) {
                LOGGER.info("Cleanup job: {} released payments, {} failed payments ready for archival", 
                    releasedCount, failedCount);
                // In production, implement archival to separate history table
            }

        } catch (Exception e) {
            LOGGER.error("Error in payment cleanup scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Monitor B2C max retries exceeded payments
     * Alert admin if payments are stuck in max retries state
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)  // Check every 5 minutes
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
     * Refund expired pending payments
     * Automatically refund payments that are stuck in PENDING after timeout
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 20000)  // Check every 5 minutes
    @Transactional
    public void refundExpiredPendingPayments() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find payments stuck in PENDING after 15 minutes
            List<EscrowPayment> expiredPayments = 
                escrowPaymentRepository.findByStatusAndTimeoutAtBefore(
                    EscrowPaymentStatus.PENDING, now);

            for (EscrowPayment payment : expiredPayments) {
                try {
                    LOGGER.info("Auto-refunding expired PENDING payment for job {}", 
                        payment.getJobRequest().getId());

                    payment.setStatus(EscrowPaymentStatus.REFUNDED);
                    payment.setMessage("Payment auto-refunded after timeout.");
                    payment.setUpdatedAt(LocalDateTime.now());
                    escrowPaymentRepository.save(payment);

                } catch (Exception e) {
                    LOGGER.error("Failed to auto-refund expired payment {}: {}", 
                        payment.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in expired payment refund scheduler: {}", e.getMessage(), e);
        }
    }
}
