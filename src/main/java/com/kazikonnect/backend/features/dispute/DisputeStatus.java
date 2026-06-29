package com.kazikonnect.backend.features.dispute;

public enum DisputeStatus {
    OPEN,                // Dispute just created
    AWAITING_EVIDENCE,   // Admin requested additional evidence
    IN_REVIEW,          // Being reviewed by admin
    RESOLVED,           // Resolution issued
    CLOSED              // Dispute fully closed
}
