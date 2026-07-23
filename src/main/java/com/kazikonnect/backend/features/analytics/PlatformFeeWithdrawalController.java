package com.kazikonnect.backend.features.analytics;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform-fees")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PlatformFeeWithdrawalController {

    private final PlatformFeeWithdrawalService withdrawalService;

    @GetMapping("/balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPlatformFeeBalance() {
        return ResponseEntity.ok(withdrawalService.getPlatformFeeBalance());
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlatformFeeWithdrawal> requestWithdrawal(
            @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(withdrawalService.requestWithdrawal(request));
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PlatformFeeWithdrawal>> getWithdrawalHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(withdrawalService.getWithdrawalHistory(page, size));
    }

    @GetMapping("/withdrawals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlatformFeeWithdrawal> getWithdrawalById(@PathVariable UUID id) {
        return withdrawalService.getWithdrawalById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/withdrawals/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlatformFeeWithdrawal> cancelWithdrawal(@PathVariable UUID id) {
        return ResponseEntity.ok(withdrawalService.cancelWithdrawal(id));
    }
}
