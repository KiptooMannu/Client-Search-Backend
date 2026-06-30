package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.common.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Job Expiration Scheduler
 * Handles automatic expiration of jobs that remain in AWAITING_FUNDING status beyond the allowed time period
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unchecked, null")
public class JobExpirationScheduler {

    private final JobRequestRepository jobRequestRepository;
    private final NotificationRepository notificationRepository;

    private static final int EXPIRY_HOURS = 48; // Configurable expiry period in hours

    /**
     * Check and expire jobs that have been awaiting funding for more than 48 hours
     * Runs every hour to check for expired jobs
     */
    @Scheduled(cron = "0 0 * * * *") // Runs every hour at the top of the hour
    @Transactional
    public void checkAndExpireJobs() {
        try {
            LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(EXPIRY_HOURS);
            
            // Find jobs that are in ACCEPTED or AWAITING_FUNDING status, not funded, and past expiry
            List<JobRequest> expiredJobs = jobRequestRepository.findExpiredJobs(expiryThreshold);

            if (!expiredJobs.isEmpty()) {
                log.info("Found {} jobs expired due to lack of funding", expiredJobs.size());
            }

            for (JobRequest job : expiredJobs) {
                try {
                    expireJob(job);
                    log.info("Expired job {} due to funding timeout", job.getId());
                } catch (Exception e) {
                    log.error("Failed to expire job {}: {}", job.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in job expiration scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Expire a single job and notify both parties
     */
    private void expireJob(JobRequest job) {
        // Update job status
        job.setStatus(JobStatus.EXPIRED);
        job.setCancellationReason("Job expired due to lack of funding within the allowed time period");
        job.setCancelledBy(UUID.fromString("00000000-0000-0000-0000-000000000000")); // System ID
        job.setCancelledAt(LocalDateTime.now());
        
        jobRequestRepository.save(job);

        // Notify client
        if (job.getClient() != null) {
            @SuppressWarnings("null")
            var clientUser = job.getClient();
            Notification clientNotification = Notification.builder()
                    .user(clientUser)
                    .title("Job Expired")
                    .message("Your job request has expired due to lack of funding within 48 hours. You can initiate a new hire with the worker.")
                    .type("WARNING")
                    .build();
            if (clientNotification != null) {
                notificationRepository.save(clientNotification);
            }
        }

        // Notify worker
        if (job.getWorker() != null && job.getWorker().getUser() != null) {
            @SuppressWarnings("null")
            var workerUser = job.getWorker().getUser();
            Notification workerNotification = Notification.builder()
                    .user(workerUser)
                    .title("Job Expired")
                    .message("The job request has expired due to lack of funding. You are now available for other opportunities.")
                    .type("WARNING")
                    .build();
            if (workerNotification != null) {
                notificationRepository.save(workerNotification);
            }
        }
    }
}

