package com.nestfind.backend.features.auth;

import com.nestfind.backend.features.worker.WorkerProfile;
import com.nestfind.backend.features.worker.WorkerProfileRepository;
import com.nestfind.backend.features.worker.WorkerStatus;
import com.nestfind.backend.features.client.ClientProfile;
import com.nestfind.backend.features.client.ClientProfileRepository;
import com.nestfind.backend.core.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

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

    // Verification Marker: Register with 5 arguments
    @Transactional
    public String register(String username, String email, String password, String fullName, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Error: Email '" + email + "' is already in use. Please try a different email or log in.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Error: Username '" + username + "' is already taken. Please choose another one.");
        }

        if (password == null || password.length() < 8) {
            throw new RuntimeException("Error: Password is too short. It must be at least 8 characters long.");
        }

        User user = User.builder()
                .username(username)
                .email(email)
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
                    .status(WorkerStatus.PENDING)
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
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, auth.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
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
            auth.getUser().getRole().name()
        );
    }
}


record AuthResponse(String accessToken, String refreshToken, String userId, String username, String email, String name, String role) {}
