package com.kazikonnect.backend.features.dev;

import com.kazikonnect.backend.features.auth.*;
import com.kazikonnect.backend.features.client.ClientProfileRepository;
import com.kazikonnect.backend.features.worker.WorkerProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevController {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final ClientProfileRepository clientProfileRepository;

    @PostMapping("/users/delete-by-email")
    @Transactional
    public ResponseEntity<?> deleteUserByEmail(@RequestParam String email) {
        String normalized = email == null ? null : email.trim().toLowerCase();
        if (normalized == null || normalized.isBlank()) {
            return ResponseEntity.badRequest().body("email is required");
        }

        userRepository.findByEmail(normalized).ifPresent(user -> {
            // delete related artifacts
            workerProfileRepository.deleteByUserId(user.getId());
            clientProfileRepository.deleteByUserId(user.getId());
            refreshTokenRepository.deleteAllByUserId(user.getId());
            passwordResetTokenRepository.deleteAllByUser(user);
            authRepository.findByUserId(user.getId()).ifPresent(authRepository::delete);
            userRepository.delete(user);
        });

        return ResponseEntity.ok(java.util.Map.of("status","ok","email",normalized));
    }
}
