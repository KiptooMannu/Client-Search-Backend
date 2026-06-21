package com.kazikonnect.backend.core.config;

import com.kazikonnect.backend.features.auth.*;
import com.kazikonnect.backend.features.common.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final NotificationRepository notificationRepository;

    @Value("${app.data-seed.enabled:true}")
    private boolean shouldSeed;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        // Drop the status check constraint to allow new statuses (CANCELLED, IN_PROGRESS)
        try {
            jdbcTemplate.execute("ALTER TABLE job_requests DROP CONSTRAINT IF EXISTS job_requests_status_check");
            log.info("Dropped job_requests_status_check constraint for compatibility.");
        } catch (Exception e) {
            log.warn("Could not drop constraint: " + e.getMessage());
        }

        syncEscrowPaymentStatusConstraint();
        backfillEscrowPaymentVersions();

        if (shouldSeed) {
            log.info("--- Starting Database Seeding ---");

            // Only seed admin - workers and clients seeding has been removed
            seedAdmin();

            // Always run name cleanup to ensure "Unknown" is replaced by real names
            fixExistingUserNames();

            log.info("--- Database Seeding Complete (Admin Only) ---");
        } else {
            log.info("--- Database Seeding Skipped (shouldSeed = false) ---");
        }
    }

    // ─── Admin Seeding ──────────────────────────────────────────────────────────
    private void seedAdmin() {
        // Delete all existing admins and their related records first
        List<User> existingAdmins = userRepository.findAllByRole(UserRole.ADMIN);

        if (!existingAdmins.isEmpty()) {
            log.info("Deleting {} existing admin user(s) before seeding default admin.", existingAdmins.size());

            for (User admin : existingAdmins) {
                try {
                    // Delete the user - cascade will handle notifications, auth, and other relationships
                    userRepository.delete(admin);
                    log.info("Deleted admin user: {}", admin.getEmail());
                } catch (Exception e) {
                    log.error("Failed to delete admin user {}: {}", admin.getEmail(), e.getMessage());
                    throw new RuntimeException("Failed to delete existing admin: " + e.getMessage(), e);
                }
            }

            // Flush changes to ensure deletion is committed before creating new admin
            userRepository.flush();
            log.info("All admin users and related records deleted successfully.");
        }

        // Create the new seeded admin user
        User u = userRepository.save(User.builder()
                .username(adminUsername).email(adminEmail)
                .firstName("System").secondName("Administrator")
                .fullName("System Administrator").role(UserRole.ADMIN).build());

        authRepository.save(Auth.builder().user(u)
                .passwordHash(passwordEncoder.encode(adminPassword)).isActive(true).emailVerified(true).build());

        // Seed Admin Notifications
        notificationRepository.save(Notification.builder()
                .user(u)
                .title("Welcome to Kazi Konnect")
                .message("System is ready. Admin account has been configured.")
                .type("INFO")
                .build());

        notificationRepository.save(Notification.builder()
                .user(u)
                .title("System Audit")
                .message("Scheduled backup completed successfully.")
                .type("SUCCESS")
                .build());

        log.info("Seeded admin with email: {}", adminEmail);
    }

    // ─── Database Maintenance ───────────────────────────────────────────────────
    private void backfillEscrowPaymentVersions() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE escrow_payments SET version = 0 WHERE version IS NULL");
            if (updated > 0) {
                log.info("Backfilled null escrow payment versions for {} row(s).", updated);
            }
        } catch (Exception e) {
            log.warn("Could not backfill escrow payment versions: {}", e.getMessage());
        }
    }

    private void syncEscrowPaymentStatusConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE escrow_payments DROP CONSTRAINT IF EXISTS escrow_payments_status_check");
            jdbcTemplate.execute("""
                ALTER TABLE escrow_payments ADD CONSTRAINT escrow_payments_status_check
                CHECK (status IN (
                    'PENDING', 'SUCCESS', 'ESCROWED', 'RELEASED', 'REFUNDED', 'FAILED',
                    'PARTIALLY_SETTLED', 'DISPUTED', 'B2C_INITIATED', 'B2C_PENDING',
                    'B2C_RETRY_PENDING', 'B2C_FAILED', 'B2C_MAX_RETRIES_EXCEEDED'
                ))
                """);
            log.info("Synced escrow_payments_status_check constraint (includes SUCCESS).");
        } catch (Exception e) {
            log.warn("Could not sync escrow payment status constraint: {}", e.getMessage());
        }
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void fixExistingUserNames() {
        log.info("--- Cleaning up User names (Optimized Bulk Update) ---");
        try {
            int both = userRepository.updateFullNamesFromFirstAndSecond();
            int first = userRepository.updateFullNamesFromFirstOnly();
            int fallback = userRepository.updateFullNamesFromUsername();
            log.info(
                    "Name cleanup results: {} fixed with full name, {} fixed with first name, {} fixed with username fallback.",
                    both, first, fallback);
        } catch (Exception e) {
            log.error("Failed to run bulk name cleanup: {}", e.getMessage());
        }
    }
}