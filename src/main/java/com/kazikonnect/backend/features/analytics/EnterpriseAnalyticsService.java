package com.kazikonnect.backend.features.analytics;

import com.kazikonnect.backend.features.analytics.EnterpriseAnalyticsDTO.Kpis;
import com.kazikonnect.backend.features.analytics.EnterpriseAnalyticsDTO.MultiSeries;
import com.kazikonnect.backend.features.analytics.EnterpriseAnalyticsDTO.NameValue;
import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.payment.EscrowPayment;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import com.kazikonnect.backend.features.payment.EscrowPaymentStatus;
import com.kazikonnect.backend.features.platformwallet.PlatformWallet;
import com.kazikonnect.backend.features.platformwallet.PlatformWalletRepository;
import com.kazikonnect.backend.features.settlementwallet.ClientSettlementWallet;
import com.kazikonnect.backend.features.settlementwallet.ClientSettlementWalletRepository;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import com.kazikonnect.backend.features.worker.JobStatus;
import com.kazikonnect.backend.features.worker.WorkerProfile;
import com.kazikonnect.backend.features.worker.WorkerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the enterprise analytics payload in a single pass over the window's
 * jobs and payments, so the dashboard costs two table scans rather than one
 * query per chart.
 */
@Service
@RequiredArgsConstructor
public class EnterpriseAnalyticsService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    /** Slice cap for categorical charts — pie/donut palettes only validate to four. */
    private static final int CATEGORY_SLICE_CAP = 4;
    /** Bar charts read fine past four, but a leaderboard past eight stops being scannable. */
    private static final int LEADERBOARD_CAP = 8;

    /** Money is in escrow: client funded it, worker hasn't been paid out yet. */
    private static final Set<EscrowPaymentStatus> HELD_IN_ESCROW = Set.of(
            EscrowPaymentStatus.SUCCESS,
            EscrowPaymentStatus.ESCROWED,
            EscrowPaymentStatus.DISPUTED
    );

    private static final Set<JobStatus> COMPLETED_STATES = Set.of(
            JobStatus.COMPLETED, JobStatus.APPROVED
    );

    private static final Set<JobStatus> PENDING_STATES = Set.of(
            JobStatus.PENDING, JobStatus.PENDING_APPLICATION, JobStatus.NEGOTIATING,
            JobStatus.ACCEPTED, JobStatus.AWAITING_FUNDING
    );

    /** Jobs that ended badly — the denominator's other half for success rate. */
    private static final Set<JobStatus> FAILED_TERMINAL_STATES = Set.of(
            JobStatus.REJECTED, JobStatus.CLIENT_CANCELLED, JobStatus.WORKER_CANCELLED,
            JobStatus.EXPIRED, JobStatus.CANCELLED, JobStatus.DISPUTED
    );

    private final EscrowPaymentRepository escrowPaymentRepository;
    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final ClientSettlementWalletRepository settlementWalletRepository;
    private final PlatformWalletRepository platformWalletRepository;

    @Transactional(readOnly = true)
    public EnterpriseAnalyticsDTO getEnterpriseAnalytics(int months) {
        int window = Math.max(1, Math.min(months, 24));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMonths(window).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        List<EscrowPayment> payments = escrowPaymentRepository.findAllByCreatedAtBetween(start, end);
        List<JobRequest> jobs = jobRequestRepository.findAllByCreatedAtBetween(start, end);
        List<User> users = userRepository.findAll();
        List<ClientSettlementWallet> wallets = settlementWalletRepository.findAll();
        PlatformWallet platformWallet = platformWalletRepository.findFirstByOrderByIdAsc().orElse(null);

        // A continuous month spine so charts never collapse gaps in sparse data.
        List<YearMonth> spine = monthSpine(start, end);

        return new EnterpriseAnalyticsDTO(
                buildKpis(payments, jobs, users, wallets, platformWallet, spine),
                revenueTrend(payments, spine),
                bookingsTrend(jobs, spine),
                earningsTrend(payments, spine),
                transactionsTrend(payments, spine),
                platformGrowth(users, spine),
                jobsByCategory(jobs),
                monthlyCount(jobs, spine, j -> COMPLETED_STATES.contains(j.getStatus())),
                monthlyCount(jobs, spine, j -> PENDING_STATES.contains(j.getStatus())),
                workerPerformance(jobs),
                clientGrowth(users, spine),
                bookingStatuses(jobs),
                paymentMethods(payments),
                escrowDistribution(payments),
                workerCategories(users),
                platformActivity(jobs, payments, spine),
                walletDistribution(wallets),
                platformFeeDistribution(platformWallet),
                userRegistrations(users)
        );
    }

    // ── KPIs ────────────────────────────────────────────────────────────────

    private Kpis buildKpis(List<EscrowPayment> payments, List<JobRequest> jobs, List<User> users,
                           List<ClientSettlementWallet> wallets, PlatformWallet platformWallet,
                           List<YearMonth> spine) {

        double totalRevenue = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> nz(p.getAmount()))
                .sum();

        double platformRevenue = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> nz(p.getPlatformFee()))
                .sum();

        double escrowBalance = payments.stream()
                .filter(p -> HELD_IN_ESCROW.contains(p.getStatus()))
                .mapToDouble(p -> nz(p.getAmount()))
                .sum();

        double pendingPayments = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.PENDING)
                .mapToDouble(p -> nz(p.getAmount()))
                .sum();

        double withdrawals = platformWallet != null ? nz(platformWallet.getTotalWithdrawn()) : 0.0;

        long activeWorkers = users.stream()
                .filter(u -> u.getRole() == UserRole.WORKER)
                .map(User::getWorkerProfile)
                .filter(p -> p != null && p.getStatus() == WorkerStatus.APPROVED)
                .count();

        // A client counts as active once they've actually posted work.
        Set<java.util.UUID> clientsWithJobs = jobs.stream()
                .filter(j -> j.getClient() != null)
                .map(j -> j.getClient().getId())
                .collect(Collectors.toSet());
        long activeClients = clientsWithJobs.size();

        long jobsCompleted = jobs.stream().filter(j -> COMPLETED_STATES.contains(j.getStatus())).count();
        long jobsPending = jobs.stream().filter(j -> PENDING_STATES.contains(j.getStatus())).count();

        long fundedJobs = jobs.stream()
                .filter(j -> Boolean.TRUE.equals(j.getEscrowFunded()))
                .count();
        double conversionRate = jobs.isEmpty() ? 0.0 : (fundedJobs * 100.0 / jobs.size());

        double averageResponseMinutes = jobs.stream()
                .filter(j -> j.getCreatedAt() != null && j.getStartedAt() != null)
                .filter(j -> !j.getStartedAt().isBefore(j.getCreatedAt()))
                .mapToLong(j -> Duration.between(j.getCreatedAt(), j.getStartedAt()).toMinutes())
                .average()
                .orElse(0.0);
        double averageResponseTimeHours = round2(averageResponseMinutes / 60.0);

        double walletBalance = wallets.stream()
                .mapToDouble(w -> nz(w.getAvailableBalance()))
                .sum();

        long terminal = jobsCompleted + jobs.stream()
                .filter(j -> FAILED_TERMINAL_STATES.contains(j.getStatus()))
                .count();
        double successRate = terminal == 0 ? 0.0 : (jobsCompleted * 100.0 / terminal);

        double monthlyGrowth = monthOverMonthRevenueGrowth(payments, spine);

        return new Kpis(
                round2(totalRevenue),
                round2(platformRevenue),
                round2(escrowBalance),
                round2(pendingPayments),
                round2(withdrawals),
                activeWorkers,
                activeClients,
                jobsCompleted,
                jobsPending,
                round2(conversionRate),
                averageResponseTimeHours,
                round2(walletBalance),
                round2(monthlyGrowth),
                round2(successRate)
        );
    }

    /** Percent change in released revenue between the last two months of the window. */
    private double monthOverMonthRevenueGrowth(List<EscrowPayment> payments, List<YearMonth> spine) {
        if (spine.size() < 2) return 0.0;
        YearMonth current = spine.get(spine.size() - 1);
        YearMonth previous = spine.get(spine.size() - 2);

        double curr = releasedRevenueIn(payments, current);
        double prev = releasedRevenueIn(payments, previous);

        if (prev == 0.0) return curr > 0.0 ? 100.0 : 0.0;
        return ((curr - prev) / prev) * 100.0;
    }

    private double releasedRevenueIn(List<EscrowPayment> payments, YearMonth month) {
        return payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .filter(p -> p.getCreatedAt() != null && YearMonth.from(p.getCreatedAt()).equals(month))
                .mapToDouble(p -> nz(p.getAmount()))
                .sum();
    }

    // ── Line charts ─────────────────────────────────────────────────────────

    private List<MultiSeries> revenueTrend(List<EscrowPayment> payments, List<YearMonth> spine) {
        Map<YearMonth, List<EscrowPayment>> released = groupReleasedByMonth(payments);
        return List.of(
                series("Gross Revenue", spine, m -> sum(released.get(m), p -> nz(p.getAmount()))),
                series("Platform Fees", spine, m -> sum(released.get(m), p -> nz(p.getPlatformFee()))),
                series("Worker Payouts", spine, m -> sum(released.get(m), p -> nz(p.getWorkerAmount())))
        );
    }

    private List<MultiSeries> bookingsTrend(List<JobRequest> jobs, List<YearMonth> spine) {
        Map<YearMonth, List<JobRequest>> byMonth = groupJobsByMonth(jobs);
        return List.of(
                series("Bookings Posted", spine, m -> (double) sizeOf(byMonth.get(m))),
                series("Bookings Funded", spine, m -> (double) countIn(byMonth.get(m),
                        j -> Boolean.TRUE.equals(j.getEscrowFunded())))
        );
    }

    private List<MultiSeries> earningsTrend(List<EscrowPayment> payments, List<YearMonth> spine) {
        Map<YearMonth, List<EscrowPayment>> released = groupReleasedByMonth(payments);
        return List.of(
                series("Worker Earnings", spine, m -> sum(released.get(m), p -> nz(p.getWorkerAmount())))
        );
    }

    private List<MultiSeries> transactionsTrend(List<EscrowPayment> payments, List<YearMonth> spine) {
        Map<YearMonth, List<EscrowPayment>> byMonth = payments.stream()
                .filter(p -> p.getCreatedAt() != null)
                .collect(Collectors.groupingBy(p -> YearMonth.from(p.getCreatedAt())));

        return List.of(
                series("All Transactions", spine, m -> (double) sizeOf(byMonth.get(m))),
                series("Successful", spine, m -> (double) countIn(byMonth.get(m),
                        p -> p.getStatus() == EscrowPaymentStatus.RELEASED
                                || HELD_IN_ESCROW.contains(p.getStatus()))),
                series("Failed", spine, m -> (double) countIn(byMonth.get(m),
                        p -> p.getStatus() == EscrowPaymentStatus.FAILED
                                || p.getStatus() == EscrowPaymentStatus.B2C_FAILED
                                || p.getStatus() == EscrowPaymentStatus.B2C_MAX_RETRIES_EXCEEDED))
        );
    }

    /** Cumulative user counts — "growth" reads as a rising total, not per-month arrivals. */
    private List<MultiSeries> platformGrowth(List<User> users, List<YearMonth> spine) {
        List<NameValue> total = new ArrayList<>();
        List<NameValue> workers = new ArrayList<>();
        List<NameValue> clients = new ArrayList<>();

        for (YearMonth month : spine) {
            LocalDateTime cutoff = month.atEndOfMonth().atTime(23, 59, 59);
            String label = month.format(MONTH_LABEL);
            total.add(new NameValue(label, (double) countRegisteredBy(users, cutoff, null)));
            workers.add(new NameValue(label, (double) countRegisteredBy(users, cutoff, UserRole.WORKER)));
            clients.add(new NameValue(label, (double) countRegisteredBy(users, cutoff, UserRole.CLIENT)));
        }

        return List.of(
                new MultiSeries("Total Users", total),
                new MultiSeries("Workers", workers),
                new MultiSeries("Clients", clients)
        );
    }

    private long countRegisteredBy(List<User> users, LocalDateTime cutoff, UserRole role) {
        return users.stream()
                .filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isAfter(cutoff))
                .filter(u -> role == null || u.getRole() == role)
                .count();
    }

    // ── Bar charts ──────────────────────────────────────────────────────────

    private List<NameValue> jobsByCategory(List<JobRequest> jobs) {
        Map<String, Long> counts = jobs.stream()
                .map(this::categoryOf)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        return topN(counts, LEADERBOARD_CAP, false);
    }

    /** Job categories come from the assigned worker's profile — jobs carry no category of their own. */
    private String categoryOf(JobRequest job) {
        WorkerProfile profile = job.getWorker();
        if (profile == null || profile.getCategory() == null || profile.getCategory().isBlank()) {
            return "Uncategorised";
        }
        return profile.getCategory().trim();
    }

    private List<NameValue> workerPerformance(List<JobRequest> jobs) {
        Map<String, Long> completedPerWorker = jobs.stream()
                .filter(j -> COMPLETED_STATES.contains(j.getStatus()))
                .filter(j -> j.getWorker() != null)
                .collect(Collectors.groupingBy(
                        j -> {
                            String name = j.getWorker().getFullName();
                            return name == null || name.isBlank() ? "Unnamed Worker" : name;
                        },
                        Collectors.counting()));
        return topN(completedPerWorker, LEADERBOARD_CAP, false);
    }

    private List<NameValue> clientGrowth(List<User> users, List<YearMonth> spine) {
        Map<YearMonth, Long> newClientsByMonth = users.stream()
                .filter(u -> u.getRole() == UserRole.CLIENT && u.getCreatedAt() != null)
                .collect(Collectors.groupingBy(u -> YearMonth.from(u.getCreatedAt()), Collectors.counting()));

        return spine.stream()
                .map(m -> new NameValue(m.format(MONTH_LABEL),
                        (double) newClientsByMonth.getOrDefault(m, 0L)))
                .toList();
    }

    private List<NameValue> monthlyCount(List<JobRequest> jobs, List<YearMonth> spine,
                                         java.util.function.Predicate<JobRequest> filter) {
        Map<YearMonth, Long> byMonth = jobs.stream()
                .filter(j -> j.getCreatedAt() != null)
                .filter(filter)
                .collect(Collectors.groupingBy(j -> YearMonth.from(j.getCreatedAt()), Collectors.counting()));

        return spine.stream()
                .map(m -> new NameValue(m.format(MONTH_LABEL), (double) byMonth.getOrDefault(m, 0L)))
                .toList();
    }

    // ── Pie charts ──────────────────────────────────────────────────────────

    private List<NameValue> bookingStatuses(List<JobRequest> jobs) {
        Map<String, Long> grouped = jobs.stream()
                .collect(Collectors.groupingBy(j -> statusBucket(j.getStatus()), Collectors.counting()));
        return topN(grouped, CATEGORY_SLICE_CAP, true);
    }

    /** Collapses 20 job states into the four a reader actually distinguishes. */
    private String statusBucket(JobStatus status) {
        if (status == null) return "Other";
        if (COMPLETED_STATES.contains(status)) return "Completed";
        if (PENDING_STATES.contains(status)) return "Pending";
        if (status == JobStatus.FUNDED || status == JobStatus.ASSIGNED
                || status == JobStatus.IN_PROGRESS || status == JobStatus.SUBMITTED
                || status == JobStatus.REVISION || status == JobStatus.REVISION_REQUESTED) {
            return "In Progress";
        }
        return "Cancelled / Disputed";
    }

    /**
     * Wallet-funded escrow rows are stamped with a {@code "WALLET"} phone number by the
     * payment service, which is what makes the split recoverable here.
     */
    private List<NameValue> paymentMethods(List<EscrowPayment> payments) {
        long wallet = payments.stream()
                .filter(p -> "WALLET".equalsIgnoreCase(p.getPhoneNumber()))
                .count();
        long mpesa = payments.stream()
                .filter(p -> p.getPhoneNumber() != null && !"WALLET".equalsIgnoreCase(p.getPhoneNumber()))
                .count();

        List<NameValue> out = new ArrayList<>();
        if (mpesa > 0) out.add(new NameValue("M-Pesa", (double) mpesa));
        if (wallet > 0) out.add(new NameValue("Wallet Balance", (double) wallet));
        return out;
    }

    private List<NameValue> escrowDistribution(List<EscrowPayment> payments) {
        double held = payments.stream()
                .filter(p -> HELD_IN_ESCROW.contains(p.getStatus()))
                .mapToDouble(p -> nz(p.getAmount())).sum();
        double released = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> nz(p.getAmount())).sum();
        double refunded = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.REFUNDED)
                .mapToDouble(p -> nz(p.getAmount())).sum();
        double pending = payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.PENDING)
                .mapToDouble(p -> nz(p.getAmount())).sum();

        return nonZero(List.of(
                new NameValue("Held in Escrow", round2(held)),
                new NameValue("Released", round2(released)),
                new NameValue("Refunded", round2(refunded)),
                new NameValue("Awaiting Payment", round2(pending))
        ));
    }

    private List<NameValue> workerCategories(List<User> users) {
        Map<String, Long> counts = users.stream()
                .filter(u -> u.getRole() == UserRole.WORKER)
                .map(User::getWorkerProfile)
                .filter(p -> p != null)
                .map(p -> p.getCategory() == null || p.getCategory().isBlank()
                        ? "Uncategorised" : p.getCategory().trim())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        return topN(counts, CATEGORY_SLICE_CAP, true);
    }

    // ── Area chart ──────────────────────────────────────────────────────────

    private List<MultiSeries> platformActivity(List<JobRequest> jobs, List<EscrowPayment> payments,
                                               List<YearMonth> spine) {
        Map<YearMonth, List<JobRequest>> jobsByMonth = groupJobsByMonth(jobs);
        Map<YearMonth, List<EscrowPayment>> paymentsByMonth = payments.stream()
                .filter(p -> p.getCreatedAt() != null)
                .collect(Collectors.groupingBy(p -> YearMonth.from(p.getCreatedAt())));

        return List.of(
                series("Jobs Created", spine, m -> (double) sizeOf(jobsByMonth.get(m))),
                series("Payments Processed", spine, m -> (double) sizeOf(paymentsByMonth.get(m))),
                series("Jobs Completed", spine, m -> (double) countIn(jobsByMonth.get(m),
                        j -> COMPLETED_STATES.contains(j.getStatus())))
        );
    }

    // ── Donut charts ────────────────────────────────────────────────────────

    private List<NameValue> walletDistribution(List<ClientSettlementWallet> wallets) {
        double available = wallets.stream().mapToDouble(w -> nz(w.getAvailableBalance())).sum();
        double pending = wallets.stream().mapToDouble(w -> nz(w.getPendingCredits())).sum();
        double withdrawn = wallets.stream().mapToDouble(w -> nz(w.getTotalWithdrawn())).sum();
        double refunded = wallets.stream().mapToDouble(w -> nz(w.getTotalRefunded())).sum();

        return nonZero(List.of(
                new NameValue("Available", round2(available)),
                new NameValue("Pending Credits", round2(pending)),
                new NameValue("Withdrawn", round2(withdrawn)),
                new NameValue("Refunded", round2(refunded))
        ));
    }

    private List<NameValue> platformFeeDistribution(PlatformWallet wallet) {
        if (wallet == null) return List.of();
        return nonZero(List.of(
                new NameValue("Available", round2(nz(wallet.getAvailableBalance()))),
                new NameValue("Pending", round2(nz(wallet.getPendingBalance()))),
                new NameValue("Withdrawn", round2(nz(wallet.getTotalWithdrawn())))
        ));
    }

    private List<NameValue> userRegistrations(List<User> users) {
        Map<UserRole, Long> byRole = users.stream()
                .filter(u -> u.getRole() != null)
                .collect(Collectors.groupingBy(User::getRole, Collectors.counting()));

        return nonZero(List.of(
                new NameValue("Clients", (double) byRole.getOrDefault(UserRole.CLIENT, 0L)),
                new NameValue("Workers", (double) byRole.getOrDefault(UserRole.WORKER, 0L)),
                new NameValue("Admins", (double) byRole.getOrDefault(UserRole.ADMIN, 0L))
        ));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private List<YearMonth> monthSpine(LocalDateTime start, LocalDateTime end) {
        List<YearMonth> spine = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            spine.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return spine;
    }

    private MultiSeries series(String name, List<YearMonth> spine,
                               java.util.function.Function<YearMonth, Double> valueAt) {
        List<NameValue> points = spine.stream()
                .map(m -> new NameValue(m.format(MONTH_LABEL), round2(valueAt.apply(m))))
                .toList();
        return new MultiSeries(name, points);
    }

    private Map<YearMonth, List<EscrowPayment>> groupReleasedByMonth(List<EscrowPayment> payments) {
        return payments.stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED && p.getCreatedAt() != null)
                .collect(Collectors.groupingBy(p -> YearMonth.from(p.getCreatedAt())));
    }

    private Map<YearMonth, List<JobRequest>> groupJobsByMonth(List<JobRequest> jobs) {
        return jobs.stream()
                .filter(j -> j.getCreatedAt() != null)
                .collect(Collectors.groupingBy(j -> YearMonth.from(j.getCreatedAt())));
    }

    /**
     * Ranks descending and keeps the top {@code cap}. When {@code foldRemainder} is set the
     * tail collapses into "Other" rather than being dropped — required for pie/donut, whose
     * slices must still sum to the whole.
     */
    private List<NameValue> topN(Map<String, Long> counts, int cap, boolean foldRemainder) {
        List<Map.Entry<String, Long>> ranked = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();

        List<NameValue> out = ranked.stream()
                .limit(cap)
                .map(e -> new NameValue(e.getKey(), (double) e.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (foldRemainder && ranked.size() > cap) {
            long other = ranked.stream().skip(cap).mapToLong(Map.Entry::getValue).sum();
            if (other > 0) out.add(new NameValue("Other", (double) other));
        }
        return out;
    }

    /** Drops empty slices so a pie of all-zeros renders as "no data" instead of a blank ring. */
    private List<NameValue> nonZero(List<NameValue> values) {
        return values.stream().filter(v -> v.value() != null && v.value() > 0).toList();
    }

    private <T> double sum(List<T> items, java.util.function.ToDoubleFunction<T> extract) {
        return items == null ? 0.0 : items.stream().mapToDouble(extract).sum();
    }

    private <T> long countIn(List<T> items, java.util.function.Predicate<T> filter) {
        return items == null ? 0L : items.stream().filter(filter).count();
    }

    private int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
