package com.kazikonnect.backend.features.worker;

public enum JobStatus {
    NEGOTIATING,        // Price/terms negotiation in progress
    PENDING,            // Initial request (legacy alias for PENDING_APPLICATION)
    PENDING_APPLICATION,// Initial request
    ACCEPTED,           // Worker accepted (awaiting funding)
    AWAITING_FUNDING,   // Worker accepted, awaiting client escrow funding
    FUNDED,             // Escrow funded, work can begin
    ASSIGNED,           // Worker assigned after payment
    IN_PROGRESS,        // Work is being done
    SUBMITTED,          // Worker says work is finished (Awaiting client approval)
    REVISION_REQUESTED, // Client wants changes before approval
    REVISION,           // Alias for REVISION_REQUESTED (Compatibility)
    APPROVED,           // Client is satisfied (Triggers payment release logic)
    COMPLETED,          // Final state after review/rating
    DISPUTED,           // Conflict opened by client
    RESOLVED,           // Dispute resolved by admin
    REJECTED,           // Worker declined initial request
    CLIENT_CANCELLED,   // Job cancelled by client before funding
    WORKER_CANCELLED,   // Worker withdrew acceptance before funding
    EXPIRED,            // Job expired due to lack of funding
    CANCELLED           // Generic cancelled (legacy)
}


