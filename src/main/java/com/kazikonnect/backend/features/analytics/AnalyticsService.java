package com.kazikonnect.backend.features.analytics;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.payment.EscrowPayment;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import com.kazikonnect.backend.features.payment.EscrowPaymentStatus;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import com.kazikonnect.backend.features.worker.JobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final EscrowPaymentRepository escrowPaymentRepository;
    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public List<RevenueData> getRevenueData(LocalDateTime startDate, LocalDateTime endDate) {
        List<EscrowPayment> payments = escrowPaymentRepository.findAllByCreatedAtBetween(startDate, endDate);

        Map<String, List<EscrowPayment>> groupedByMonth = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getCreatedAt().format(MONTH_FORMATTER)));

        return groupedByMonth.entrySet().stream()
                .map(entry -> {
                    List<EscrowPayment> monthPayments = entry.getValue();
                    double revenue = monthPayments.stream()
                            .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                            .mapToDouble(p -> p.getAmount())
                            .sum();
                    double platformFees = monthPayments.stream()
                            .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                            .mapToDouble(p -> p.getPlatformFee() != null ? p.getPlatformFee() : 0)
                            .sum();
                    double workerPayouts = monthPayments.stream()
                            .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                            .mapToDouble(p -> p.getWorkerAmount() != null ? p.getWorkerAmount() : 0)
                            .sum();

                    return RevenueData.builder()
                            .period(entry.getKey())
                            .revenue(revenue)
                            .platformFees(platformFees)
                            .workerPayouts(workerPayouts)
                            .build();
                })
                .sorted(Comparator.comparing(r -> r.getPeriod()))
                .collect(Collectors.toList());
    }

    public UserGrowthData getUserGrowthData(LocalDateTime startDate, LocalDateTime endDate) {
        List<User> allUsers = userRepository.findAll();
        
        long newClients = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.CLIENT)
                .filter(u -> u.getCreatedAt() != null && 
                             (u.getCreatedAt().isAfter(startDate) || u.getCreatedAt().isEqual(startDate)) && 
                             u.getCreatedAt().isBefore(endDate))
                .count();
        
        long newWorkers = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.WORKER)
                .filter(u -> u.getCreatedAt() != null && 
                             (u.getCreatedAt().isAfter(startDate) || u.getCreatedAt().isEqual(startDate)) && 
                             u.getCreatedAt().isBefore(endDate))
                .count();
        
        long totalUsers = allUsers.size();
        
        return UserGrowthData.builder()
                .period(startDate.format(MONTH_FORMATTER) + " to " + endDate.format(MONTH_FORMATTER))
                .newClients((int) newClients)
                .newWorkers((int) newWorkers)
                .totalUsers((int) totalUsers)
                .build();
    }

    public JobStatisticsData getJobStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        List<JobRequest> jobs = jobRequestRepository.findAllByCreatedAtBetween(startDate, endDate);

        long jobsPosted = jobs.size();
        long jobsCompleted = jobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
        long jobsInProgress = jobs.stream().filter(j -> j.getStatus() == JobStatus.IN_PROGRESS).count();
        long jobsPending = jobs.stream().filter(j -> j.getStatus() == JobStatus.PENDING).count();

        // Since JobRequest doesn't have a category field, we'll use description/title as a proxy
        // or return empty category data for now
        Map<String, Integer> categoryCounts = new HashMap<>();
        categoryCounts.put("General", (int) jobsPosted);

        return JobStatisticsData.builder()
                .period(startDate.format(MONTH_FORMATTER) + " to " + endDate.format(MONTH_FORMATTER))
                .jobsPosted((int) jobsPosted)
                .jobsCompleted((int) jobsCompleted)
                .jobsInProgress((int) jobsInProgress)
                .jobsPending((int) jobsPending)
                .jobsByCategory(categoryCounts)
                .build();
    }

    public PlatformFeeData getPlatformFeeData(LocalDateTime startDate, LocalDateTime endDate) {
        List<EscrowPayment> payments = escrowPaymentRepository.findAllByCreatedAtBetween(startDate, endDate);

        double totalFees = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> p.getPlatformFee() != null ? p.getPlatformFee() : 0)
                .sum();

        // For now, assuming all fees are available for withdrawal
        // This would need to be adjusted based on actual withdrawal tracking
        double availableForWithdrawal = totalFees;
        double withdrawn = 0.0;
        double pending = 0.0;

        return PlatformFeeData.builder()
                .period(startDate.format(MONTH_FORMATTER) + " to " + endDate.format(MONTH_FORMATTER))
                .totalFees(totalFees)
                .availableForWithdrawal(availableForWithdrawal)
                .withdrawn(withdrawn)
                .pending(pending)
                .build();
    }

    public Map<String, Object> getDashboardOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        List<EscrowPayment> recentPayments = escrowPaymentRepository.findAllByCreatedAtBetween(thirtyDaysAgo, now);
        List<JobRequest> recentJobs = jobRequestRepository.findAllByCreatedAtBetween(thirtyDaysAgo, now);

        double totalRevenue = recentPayments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> p.getAmount())
                .sum();

        double totalPlatformFees = recentPayments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> p.getPlatformFee() != null ? p.getPlatformFee() : 0)
                .sum();

        long totalJobs = recentJobs.size();
        long completedJobs = recentJobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalRevenue", totalRevenue);
        overview.put("totalPlatformFees", totalPlatformFees);
        overview.put("totalJobs", totalJobs);
        overview.put("completedJobs", completedJobs);
        overview.put("completionRate", totalJobs > 0 ? (completedJobs * 100.0 / totalJobs) : 0);
        overview.put("availableForWithdrawal", totalPlatformFees);

        return overview;
    }

    public List<ClientSpendingData> getClientSpendingData(String clientId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            UUID client = UUID.fromString(clientId);
            List<EscrowPayment> payments = escrowPaymentRepository.findAllByCreatedAtBetween(startDate, endDate)
                    .stream()
                    .filter(p -> p.getJobRequest() != null
                            && p.getJobRequest().getClient() != null
                            && client.equals(p.getJobRequest().getClient().getId()))
                    .toList();

            Map<String, List<EscrowPayment>> groupedByMonth = payments.stream()
                    .collect(Collectors.groupingBy(p -> p.getCreatedAt().format(MONTH_FORMATTER)));

            return groupedByMonth.entrySet().stream()
                    .map((Map.Entry<String, List<EscrowPayment>> entry) -> {
                        List<EscrowPayment> monthPayments = entry.getValue();
                        double totalSpent = monthPayments.stream()
                                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0)
                                .sum();

                        return ClientSpendingData.builder()
                                .period(entry.getKey())
                                .amount(totalSpent)
                                .build();
                    })
                    .sorted(Comparator.comparing(d -> d.getPeriod()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<WorkerEarningsData> getWorkerEarningsData(String workerId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            UUID worker = UUID.fromString(workerId);
            List<EscrowPayment> payments = escrowPaymentRepository.findAllByCreatedAtBetween(startDate, endDate)
                    .stream()
                    .filter(p -> p.getJobRequest() != null
                            && p.getJobRequest().getWorker() != null
                            && worker.equals(p.getJobRequest().getWorker().getId()))
                    .toList();

            Map<String, List<EscrowPayment>> groupedByMonth = payments.stream()
                    .collect(Collectors.groupingBy(p -> p.getCreatedAt().format(MONTH_FORMATTER)));

            return groupedByMonth.entrySet().stream()
                    .map((Map.Entry<String, List<EscrowPayment>> entry) -> {
                        List<EscrowPayment> monthPayments = entry.getValue();
                        double totalEarnings = monthPayments.stream()
                                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                                .mapToDouble(p -> p.getWorkerAmount() != null ? p.getWorkerAmount() : 0)
                                .sum();

                        return WorkerEarningsData.builder()
                                .period(entry.getKey())
                                .earnings(totalEarnings)
                                .jobsCompleted(monthPayments.size())
                                .build();
                    })
                    .sorted(Comparator.comparing(d -> d.getPeriod()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
