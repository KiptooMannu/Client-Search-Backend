package com.kazikonnect.backend.features.wallet;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record WalletTransactionDTO(
    UUID id,
    WalletTransactionType txnType,
    Double amount,
    Double balanceAfter,
    String description,
    LocalDateTime createdAt
) {}
