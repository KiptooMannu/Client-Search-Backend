package com.kazikonnect.backend.features.settlementwallet;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SettlementWalletSummaryDTO {
    private Double availableBalance;
    private Double pendingCredits;
    private Double totalRefunded;
    private Double totalWithdrawn;
    private Double totalSettlementCredits;
    private Boolean isFrozen;
    private String freezeReason;
    private Double refundedToday;
    private Double refundedThisWeek;
    private Double refundedThisMonth;
    private Double withdrawnToday;
    private Double withdrawnThisWeek;
    private Double withdrawnThisMonth;
}
