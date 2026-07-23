package com.kazikonnect.backend.features.analytics;

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
                            .mapToDouble(EscrowPayment::getAmount)
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
                .sorted(Comparator.comparing(RevenueData::getPeriod))
                .collect(Collectors.toList());
    }

    public UserGrowthData getUserGrowthData(LocalDateTime startDate, LocalDateTime endDate) {
        // This would need user repositories - for now returning placeholder
        return UserGrowthData.builder()
                .period(startDate.format(MONTH_FORMATTER) + " to " + endDate.format(MONTH_FORMATTER))
                .newClients(0)
                .newWorkers(0)
                .totalUsers(0)
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
                .mapToDouble(EscrowPayment::getAmount)
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
        // This would need client-specific payment tracking
        // For now returning placeholder
        return Collections.emptyList();
    }

    public List<WorkerEarningsData> getWorkerEarningsData(String workerId, LocalDateTime startDate, LocalDateTime endDate) {
        // This would need worker-specific payment tracking
        // For now returning placeholder
        return Collections.emptyList();
    }
}
