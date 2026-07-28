package com.kazikonnect.backend.features.analytics;

import java.util.List;

/**
 * Single payload backing the enterprise analytics dashboard.
 * <p>
 * Series are emitted in the shape the frontend charting library consumes directly:
 * single-series charts (bar / pie / donut) as {@link NameValue} lists, and
 * multi-series charts (line / area) as {@link MultiSeries} lists. Keeping the
 * shaping here means the dashboard renders without client-side reshaping.
 */
public record EnterpriseAnalyticsDTO(
        Kpis kpis,

        // Line charts
        List<MultiSeries> revenueTrend,
        List<MultiSeries> bookingsTrend,
        List<MultiSeries> earningsTrend,
        List<MultiSeries> transactionsTrend,
        List<MultiSeries> platformGrowth,

        // Bar charts
        List<NameValue> jobsByCategory,
        List<NameValue> completedJobs,
        List<NameValue> pendingJobs,
        List<NameValue> workerPerformance,
        List<NameValue> clientGrowth,

        // Pie charts
        List<NameValue> bookingStatuses,
        List<NameValue> paymentMethods,
        List<NameValue> escrowDistribution,
        List<NameValue> workerCategories,

        // Area chart
        List<MultiSeries> platformActivity,

        // Donut charts
        List<NameValue> walletDistribution,
        List<NameValue> platformFeeDistribution,
        List<NameValue> userRegistrations
) {

    /** A single {name, value} datum — the canonical shape for categorical charts. */
    public record NameValue(String name, Double value) {}

    /** A named series of {@link NameValue} points, for time-series charts. */
    public record MultiSeries(String name, List<NameValue> series) {}

    /**
     * The 14 headline figures.
     *
     * @param averageResponseTimeHours mean hours from job creation to work starting;
     *                                 derived from {@code createdAt → startedAt}, which is the
     *                                 closest signal the schema records for worker responsiveness.
     * @param conversionRate           share of posted jobs that reached escrow funding.
     * @param successRate              completed jobs as a share of all jobs that reached a terminal state.
     * @param monthlyGrowth            percent change in revenue, current month vs. previous.
     */
    public record Kpis(
            double totalRevenue,
            double platformRevenue,
            double escrowBalance,
            double pendingPayments,
            double withdrawals,
            long activeWorkers,
            long activeClients,
            long jobsCompleted,
            long jobsPending,
            double conversionRate,
            double averageResponseTimeHours,
            double walletBalance,
            double monthlyGrowth,
            double successRate
    ) {}
}
