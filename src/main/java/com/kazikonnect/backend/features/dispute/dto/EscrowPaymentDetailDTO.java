package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowPaymentDetailDTO {
    private UUID id;
    private Double amount;
    private Double platformFee;
    private Double workerAmount;
    private String status;
    private String mpesaReceiptNumber;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;
    private String message;
}
