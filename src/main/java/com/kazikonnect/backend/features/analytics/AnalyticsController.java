package com.kazikonnect.backend.features.analytics;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.worker.WorkerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final EnterpriseAnalyticsService enterpriseAnalyticsService;
    private final com.kazikonnect.backend.features.worker.WorkerProfileRepository workerProfileRepository;
    private final UserRepository userRepository;

    /**
     * Everything the enterprise dashboard renders — all KPIs and every chart series —
     * in one call, so the dashboard doesn't fan out into twenty requests.
     */
    @GetMapping("/enterprise")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<EnterpriseAnalyticsDTO> getEnterpriseAnalytics(
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(enterpriseAnalyticsService.getEnterpriseAnalytics(months));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<List<RevenueData>> getRevenueData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(6);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(analyticsService.getRevenueData(startDate, endDate));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<UserGrowthData> getUserGrowthData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(6);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(analyticsService.getUserGrowthData(startDate, endDate));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<JobStatisticsData> getJobStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(6);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(analyticsService.getJobStatistics(startDate, endDate));
    }

    @GetMapping("/platform-fees")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<PlatformFeeData> getPlatformFeeData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(6);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(analyticsService.getPlatformFeeData(startDate, endDate));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        return ResponseEntity.ok(analyticsService.getDashboardOverview());
    }

    @GetMapping("/client/{clientId}/spending")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<List<ClientSpendingData>> getClientSpendingData(
            @PathVariable String clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Principal principal) {
        requireSelfOrAdmin(principal, clientId);
        return ResponseEntity.ok(analyticsService.getClientSpendingData(clientId, startDate, endDate));
    }

    @GetMapping("/worker/{workerId}/earnings")
    @PreAuthorize("hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<List<WorkerEarningsData>> getWorkerEarningsData(
            @PathVariable String workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Principal principal) {
        // A worker's earnings are keyed by their WorkerProfile id, so compare against that.
        User actor = resolveActor(principal);
        if (actor.getRole() != UserRole.ADMIN) {
            UUID profileId;
            try {
                profileId = UUID.fromString(workerId);
            } catch (IllegalArgumentException ex) {
                throw new AccessDeniedException("You may only view your own earnings.");
            }
            if (!workerProfileRepository.existsByIdAndUserId(profileId, actor.getId())) {
                throw new AccessDeniedException("You may only view your own earnings.");
            }
        }
        return ResponseEntity.ok(analyticsService.getWorkerEarningsData(workerId, startDate, endDate));
    }

    /** Blocks a non-admin from reading another user's analytics via the path parameter. */
    private void requireSelfOrAdmin(Principal principal, String targetUserId) {
        User actor = resolveActor(principal);
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!actor.getId().toString().equalsIgnoreCase(targetUserId)) {
            throw new AccessDeniedException("You may only view your own analytics.");
        }
    }

    private User resolveActor(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication auth
                && auth.getPrincipal() instanceof User user) {
            return user;
        }

        if (principal instanceof org.springframework.security.core.Authentication auth
                && auth.getPrincipal() instanceof String username) {
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new AccessDeniedException("Unable to resolve the authenticated user."));
        }

        throw new AccessDeniedException("Unable to resolve the authenticated user.");
    }
}
