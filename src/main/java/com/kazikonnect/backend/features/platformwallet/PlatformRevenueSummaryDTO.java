package com.kazikonnect.backend.features.platformwallet;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformRevenueSummaryDTO {
    private Double totalRevenueEarned;
    private Double availableBalance;
    private Double pendingBalance;
    private Double totalWithdrawn;
    private Double revenueToday;
    private Double revenueThisWeek;
    private Double revenueThisMonth;
    private Double revenueThisYear;
    private Long totalCompletedTransactions;
    private Long transactionsToday;
    private Long transactionsThisWeek;
    private Long transactionsThisMonth;
    private Long transactionsThisYear;
    private Double averagePlatformFeePerTransaction;
}
