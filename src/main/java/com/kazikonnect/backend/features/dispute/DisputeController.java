package com.kazikonnect.backend.features.dispute;

import com.kazikonnect.backend.features.dispute.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    // ─────────────────────────────────────────────────────────────────────────
    // FILE DISPUTE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * File a dispute for a booking
     * POST /api/disputes/file
     */
    @PostMapping("/file")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker')")
    public ResponseEntity<Map<String, Object>> fileDispute(
            @RequestBody FileDisputeRequest request,
            Principal principal) {
        log.info("Filing dispute for job: {} by user: {}", request.getJobId(), principal.getName());
        
        Dispute dispute = disputeService.fileDispute(request, principal);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "DISPUTE_FILED",
                "disputeId", dispute.getId(),
                "message", "Dispute successfully filed. The escrow payment is now locked and under review."
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVIDENCE MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add evidence to a dispute
     * POST /api/disputes/{disputeId}/evidence
     */
    @PostMapping("/{disputeId}/evidence")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> addEvidence(
            @PathVariable UUID disputeId,
            @RequestBody List<FileEvidenceDTO> evidence,
            Principal principal) {
        log.info("Adding evidence to dispute: {} by user: {}", disputeId, principal.getName());
        
        disputeService.addEvidence(disputeId, evidence, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "EVIDENCE_ADDED",
                "message", "Evidence successfully uploaded",
                "count", evidence.size()
        ));
    }

    /**
     * Request evidence from a party
     * POST /api/disputes/{disputeId}/request-evidence
     */
    @PostMapping("/{disputeId}/request-evidence")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> requestEvidence(
            @PathVariable UUID disputeId,
            @RequestBody RequestEvidenceDTO request,
            Principal principal) {
        log.info("Requesting evidence for dispute: {} by admin: {}", disputeId, principal.getName());
        
        request.setDisputeId(disputeId);
        DisputeEvidenceRequest evidenceRequest = disputeService.requestEvidence(request, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "EVIDENCE_REQUESTED",
                "requestId", evidenceRequest.getId(),
                "message", "Evidence request sent successfully",
                "dueDate", evidenceRequest.getDueDate()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MESSAGING & COMMUNICATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a message to dispute
     * POST /api/disputes/{disputeId}/messages
     */
    @PostMapping("/{disputeId}/messages")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> addMessage(
            @PathVariable UUID disputeId,
            @RequestBody AddDisputeMessageDTO request,
            Principal principal) {
        log.info("Adding message to dispute: {} by user: {}", disputeId, principal.getName());
        
        request.setDisputeId(disputeId);
        DisputeMessage message = disputeService.addMessage(request, principal);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "MESSAGE_ADDED",
                "messageId", message.getId(),
                "message", "Message successfully posted"
        ));
    }

    /**
     * Get all messages for a dispute
     * GET /api/disputes/{disputeId}/messages
     */
    @GetMapping("/{disputeId}/messages")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable UUID disputeId,
            Principal principal) {
        log.info("Fetching messages for dispute: {}", disputeId);
        
        DisputeDetailDTO detail = disputeService.getDisputeDetail(disputeId, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "messages", detail.getMessages(),
                "count", detail.getMessages() != null ? detail.getMessages().size() : 0
        ));
    }

    /**
     * Mark message as read
     * PUT /api/disputes/messages/{messageId}/read
     */
    @PutMapping("/messages/{messageId}/read")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, String>> markMessageAsRead(
            @PathVariable UUID messageId,
            Principal principal) {
        log.info("Marking message as read: {} by user: {}", messageId, principal.getName());
        
        disputeService.markMessageAsRead(messageId, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Message marked as read"
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPUTE RESOLUTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve a dispute
     * POST /api/disputes/{disputeId}/resolve
     */
    @PostMapping("/{disputeId}/resolve")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> resolveDispute(
            @PathVariable UUID disputeId,
            @RequestBody ResolvDisputeRequest request,
            Principal principal) {
        log.info("Resolving dispute: {} by admin: {}", disputeId, principal.getName());
        
        request.setDisputeId(disputeId);
        Dispute dispute = disputeService.resolveDispute(request, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "DISPUTE_RESOLVED",
                "disputeId", dispute.getId(),
                "resolutionType", dispute.getResolutionType().toString(),
                "message", "Dispute successfully resolved. Funds have been distributed."
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assign dispute to admin
     * PUT /api/disputes/{disputeId}/assign
     */
    @PutMapping("/{disputeId}/assign")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> assignDispute(
            @PathVariable UUID disputeId,
            @RequestBody AssignDisputeDTO request,
            Principal principal) {
        log.info("Assigning dispute: {} to admin: {}", disputeId, request.getAdminId());
        
        request.setDisputeId(disputeId);
        Dispute dispute = disputeService.assignDisputeToAdmin(request, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "ASSIGNED",
                "disputeId", dispute.getId(),
                "assignedToAdminId", dispute.getAssignedToAdmin().getId(),
                "message", "Dispute successfully assigned"
        ));
    }

    /**
     * Change dispute priority
     * PUT /api/disputes/{disputeId}/priority
     */
    @PutMapping("/{disputeId}/priority")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> changePriority(
            @PathVariable UUID disputeId,
            @RequestBody Map<String, String> request,
            Principal principal) {
        log.info("Changing priority for dispute: {} to: {}", disputeId, request.get("priority"));
        
        Dispute dispute = disputeService.changePriority(disputeId, request.get("priority"), principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "PRIORITY_CHANGED",
                "disputeId", dispute.getId(),
                "newPriority", dispute.getPriority().toString(),
                "message", "Priority successfully changed"
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETRIEVE DISPUTE DATA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Get full dispute details
     * GET /api/disputes/{disputeId}
     */
    @GetMapping("/{disputeId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> getDisputeDetail(
            @PathVariable UUID disputeId,
            Principal principal) {
        log.info("Fetching dispute detail: {}", disputeId);
        
        DisputeDetailDTO detail = disputeService.getDisputeDetail(disputeId, principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "dispute", detail
        ));
    }

    /**
     * Get disputes for current user
     * GET /api/disputes/my
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker')")
    public ResponseEntity<Map<String, Object>> getUserDisputes(Principal principal) {
        log.info("Fetching disputes for user: {}", principal.getName());
        
        List<DisputeDetailDTO> disputes = disputeService.getUserDisputes(principal);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "disputes", disputes,
                "count", disputes.size()
        ));
    }

    /**
     * Get admin assigned disputes
     * GET /api/disputes/admin/{adminId}
     */
    @GetMapping("/admin/{adminId}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> getAdminDisputes(
            @PathVariable UUID adminId,
            Pageable pageable) {
        log.info("Fetching disputes for admin: {}", adminId);
        
        Page<DisputeListItemDTO> disputes = disputeService.getAdminDisputeList(adminId, pageable);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "disputes", disputes.getContent(),
                "totalPages", disputes.getTotalPages(),
                "totalElements", disputes.getTotalElements(),
                "currentPage", disputes.getNumber()
        ));
    }

    /**
     * Get unassigned disputes
     * GET /api/disputes/unassigned
     */
    @GetMapping("/unassigned")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> getUnassignedDisputes() {
        log.info("Fetching unassigned disputes");
        
        List<DisputeListItemDTO> disputes = disputeService.getUnassignedDisputes();
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "disputes", disputes,
                "count", disputes.size()
        ));
    }

    /**
     * Get audit trail for dispute
     * GET /api/disputes/{disputeId}/audit-trail
     */
    @GetMapping("/{disputeId}/audit-trail")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Map<String, Object>> getAuditTrail(@PathVariable UUID disputeId) {
        log.info("Fetching audit trail for dispute: {}", disputeId);
        
        List<AuditTrailDTO> auditTrail = disputeService.getDisputeAuditTrail(disputeId);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "auditTrail", auditTrail,
                "count", auditTrail.size()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR HANDLING
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        log.error("Error processing dispute request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", "ERROR",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unexpected error processing dispute request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "ERROR",
                "message", "An unexpected error occurred"
        ));
    }
}
