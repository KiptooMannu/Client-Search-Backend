package com.kazikonnect.backend.features.auth;

import com.kazikonnect.backend.features.worker.WorkerProfile;
import com.kazikonnect.backend.features.worker.WorkerProfileRepository;
import com.kazikonnect.backend.features.worker.WorkerStatus;
import com.kazikonnect.backend.features.client.ClientProfile;
import com.kazikonnect.backend.features.client.ClientProfileRepository;
import com.kazikonnect.backend.core.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthService {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    // Verification Marker: Register with 5 arguments
    @Transactional
    public String register(String username, String email, String password, String firstName, String secondName, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Error: Email '" + email + "' is already in use. Please try a different email or log in.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Error: Username '" + username + "' is already taken. Please choose another one.");
        }

        if (password == null || password.length() < 8) {
            throw new RuntimeException("Error: Password is too short. It must be at least 8 characters long.");
        }

        String fullName = firstName + " " + secondName;

        User user = User.builder()
                .username(username)
                .email(email)
                .firstName(firstName)
                .secondName(secondName)
                .fullName(fullName)
                .role(role)
                .build();
        user = userRepository.save(user);

        Auth auth = Auth.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(password))
                .isActive(true)
                .build();
        authRepository.save(auth);

        // Initialize Profile based on role
        if (role == UserRole.WORKER) {
            WorkerProfile workerProfile = WorkerProfile.builder()
                    .user(user)
                    .fullName(fullName)
                    .status(WorkerStatus.DRAFT)
                    .isVisible(false)
                    .isOnline(false)
                    .build();
            workerProfileRepository.save(workerProfile);
        } else if (role == UserRole.CLIENT) {
            ClientProfile clientProfile = ClientProfile.builder()
                    .user(user)
                    .fullName(fullName)
                    .build();
            clientProfileRepository.save(clientProfile);
        }

        return "User registered successfully";
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        Auth auth = authRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found. Please register."));

        if (!auth.isActive()) {
            throw new RuntimeException("Account is suspended. Please contact support.");
        }

        if (!passwordEncoder.matches(password, auth.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password.");
        }

        auth.setLastLogin(LocalDateTime.now());
        authRepository.save(auth);

        String token = jwtTokenProvider.generateToken(auth.getUser().getUsername(), auth.getUser().getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        // Save refresh token
        RefreshToken rt = RefreshToken.builder()
                .user(auth.getUser())
                .token(refreshToken)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(rt);

        String name = auth.getUser().getFullName();
        if (name == null || name.isBlank()) {
            name = auth.getUser().getUsername();
        }

        return new AuthResponse(
            token, 
            refreshToken, 
            auth.getUser().getId().toString(),
            auth.getUser().getUsername(), 
            auth.getUser().getEmail(),
            name,
            auth.getUser().getRole().name(),
            auth.getUser().getProfilePictureUrl()
        );
    }

    @Transactional
    public void initializePasswordReset(String email) {
        String normalizedEmail = Optional.ofNullable(email)
            .map(String::trim)
            .filter(str -> !str.isBlank())
            .orElseThrow(() -> new RuntimeException("Error: Email is required."));

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            String resetToken = java.util.UUID.randomUUID().toString();
            long expiryTime = System.currentTimeMillis() + (15 * 60 * 1000); // 15 minutes expiry

            // Store token in session/cache (simplified for now - stores in memory)
            // In production, use Redis or a database table for persistence
            RESET_TOKENS.put(resetToken, new PasswordResetToken(user.getId(), expiryTime));

            // Send email with reset link
            emailService.sendPasswordResetEmail(normalizedEmail, resetToken);
        });
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new RuntimeException("Error: Password must be at least 8 characters long.");
        }

        PasswordResetToken tokenData = RESET_TOKENS.get(resetToken);
        if (tokenData == null) {
            throw new RuntimeException("Error: Invalid or expired reset token.");
        }

        if (System.currentTimeMillis() > tokenData.expiryTime) {
            RESET_TOKENS.remove(resetToken);
            throw new RuntimeException("Error: Reset token has expired. Please request a new one.");
        }

        User user = userRepository.findById(tokenData.userId)
            .orElseThrow(() -> new RuntimeException("Error: User not found."));

        Auth auth = authRepository.findByUserId(user.getId())
            .orElseThrow(() -> new RuntimeException("Error: Auth record not found."));

        auth.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(newPassword));
        authRepository.save(auth);

        // Invalidate token after use
        RESET_TOKENS.remove(resetToken);
    }

    // In-memory token storage (for demo - use Redis/DB in production)
    private static final java.util.Map<String, PasswordResetToken> RESET_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    private static class PasswordResetToken {
        UUID userId;
        long expiryTime;
        
        PasswordResetToken(UUID userId, long expiryTime) {
            this.userId = userId;
            this.expiryTime = expiryTime;
        }
    }
}


record AuthResponse(String accessToken, String refreshToken, String userId, String username, String email, String name, String role, String profilePictureUrl) {}
