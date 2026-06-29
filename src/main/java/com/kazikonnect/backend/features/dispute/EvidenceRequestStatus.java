package com.kazikonnect.backend.features.dispute;

public enum EvidenceRequestStatus {
    PENDING,      // Waiting for evidence
    PROVIDED,     // Evidence provided
    SATISFIED,    // Evidence satisfies the request
    OVERDUE       // Evidence request deadline passed
}
