package com.kazikonnect.backend.features.worker;

public enum JobStatus {
    PENDING,            // Initial request
    ACCEPTED,           // Worker accepted
    IN_PROGRESS,        // Work is being done
    SUBMITTED,          // Worker says work is finished (Awaiting client approval)
    REVISION_REQUESTED, // Client wants changes before approval
    REVISION,           // Alias for REVISION_REQUESTED (Compatibility)
    APPROVED,           // Client is satisfied (Triggers payment release logic)
    COMPLETED,          // Final state after review/rating
    DISPUTED,           // Conflict opened by client
    REJECTED,           // Worker declined initial request
    CANCELLED           // Job cancelled by client/worker
}

