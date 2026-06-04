package com.kazikonnect.backend.features.auth;

import com.kazikonnect.backend.features.worker.WorkerProfile;
import com.kazikonnect.backend.features.worker.WorkerProfileRepository;
import com.kazikonnect.backend.features.worker.WorkerStatus;
import com.kazikonnect.backend.features.client.ClientProfile;
import com.kazikonnect.backend.features.client.ClientProfileRepository;
import com.kazikonnect.backend.core.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AuthService {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    // Verification Marker: Register with 5 arguments
    @Transactional
    public String register(String username, String email, String password, String firstName, String secondName, UserRole role) {
        if (role == UserRole.ADMIN) {
            throw new com.kazikonnect.backend.features.auth.AuthException("The ADMIN role cannot be selected during public registration.", HttpStatus.BAD_REQUEST);
        }

        String normalizedEmail = normalizeEmail(email);

        if (password == null || password.length() < 8) {
            throw new com.kazikonnect.backend.features.auth.AuthException("Password must be at least 8 characters long.", HttpStatus.BAD_REQUEST);
        }

        String fullName = firstName + " " + secondName;

        try {
            Optional<User> existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail);

            if (existingUser.isPresent()) {
                User user = existingUser.get();
                Auth auth = authRepository.findByUserId(user.getId())
                        .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("This email is already registered. Please log in or use a different email.", HttpStatus.CONFLICT));

                if (Boolean.TRUE.equals(auth.getEmailVerified())) {
                    throw new com.kazikonnect.backend.features.auth.AuthException("This email is already registered. Please log in or use a different email.", HttpStatus.CONFLICT);
                }

                if (userRepository.existsByUsernameIgnoreCase(username) && !user.getUsername().equalsIgnoreCase(username)) {
                    throw new com.kazikonnect.backend.features.auth.AuthException("That username is already taken. Please choose another one.", HttpStatus.CONFLICT);
                }

                user.setUsername(username);
                user.setFirstName(firstName);
                user.setSecondName(secondName);
                user.setFullName(fullName);
                user.setRole(role);
                user.setEmail(normalizedEmail);
                user = userRepository.save(user);
                userRepository.flush();

                String verificationToken = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);
                auth.setPasswordHash(passwordEncoder.encode(password));
                auth.setActive(true);
                auth.setEmailVerified(false);
                auth.setEmailVerificationToken(verificationToken);
                auth.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
                auth.setUser(user);
                authRepository.save(auth);

                ensureProfileForRole(user, role, fullName);

                emailService.sendEmailVerificationEmail(normalizedEmail, fullName, verificationToken);
                return "User registered successfully. Please check your email to verify your account.";
            }

            if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw new com.kazikonnect.backend.features.auth.AuthException("This email is already registered. Please log in or use a different email.", HttpStatus.CONFLICT);
            }

            if (userRepository.existsByUsernameIgnoreCase(username)) {
                throw new com.kazikonnect.backend.features.auth.AuthException("That username is already taken. Please choose another one.", HttpStatus.CONFLICT);
            }

            User user = User.builder()
                    .username(username)
                    .email(normalizedEmail)
                    .firstName(firstName)
                    .secondName(secondName)
                    .fullName(fullName)
                    .role(role)
                    .build();
            user = userRepository.save(user);
            userRepository.flush();

            String verificationToken = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);

            Auth auth = new Auth();
            auth.setUser(user);
            auth.setPasswordHash(passwordEncoder.encode(password));
            auth.setActive(true);
            auth.setEmailVerified(false);
            auth.setEmailVerificationToken(verificationToken);
            auth.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));

            authRepository.save(auth);
            authRepository.flush();
            log.info("Created auth record {} for user {} with verification token {}", auth.getId(), normalizedEmail, verificationToken);
            authRepository.findById(auth.getId()).ifPresent(savedAuth ->
                log.info("Persisted auth row for id {} has token={} verified={}", savedAuth.getId(), savedAuth.getEmailVerificationToken(), Boolean.TRUE.equals(savedAuth.getEmailVerified()))
            );

            ensureProfileForRole(user, role, fullName);

            emailService.sendEmailVerificationEmail(normalizedEmail, fullName, verificationToken);

            return "User registered successfully. Please check your email to verify your account.";
        } catch (DataIntegrityViolationException | PersistenceException ex) {
            String message = "Registration failed because email or username already exists. Please use different credentials.";
            Throwable cause = ex instanceof DataIntegrityViolationException
                    ? ((DataIntegrityViolationException) ex).getMostSpecificCause()
                    : ex.getCause();

            while (cause != null) {
                String causeMessage = cause.getMessage();
                if (causeMessage != null) {
                    String normalizedCause = causeMessage.toLowerCase();
                    if (normalizedCause.contains("username")) {
                        message = "That username is already taken. Please choose another one.";
                        break;
                    } else if (normalizedCause.contains("email")) {
                        message = "This email is already registered. Please log in or use a different email.";
                        break;
                    }
                }
                cause = cause.getCause();
            }

            throw new com.kazikonnect.backend.features.auth.AuthException(message, HttpStatus.CONFLICT);
        }
    }

    private void ensureProfileForRole(User user, UserRole role, String fullName) {
        Optional<WorkerProfile> workerOpt = workerProfileRepository.findByUserId(user.getId());
        Optional<ClientProfile> clientOpt = clientProfileRepository.findByUserId(user.getId());

        if (role == UserRole.WORKER) {
            if (clientOpt.isPresent()) {
                clientProfileRepository.delete(clientOpt.get());
                clientProfileRepository.flush();
            }
            if (workerOpt.isPresent()) {
                WorkerProfile workerProfile = workerOpt.get();
                workerProfile.setFullName(fullName);
                workerProfile.setStatus(WorkerStatus.DRAFT);
                workerProfile.setVisible(false);
                workerProfile.setOnline(false);
                workerProfileRepository.save(workerProfile);
            } else {
                WorkerProfile workerProfile = WorkerProfile.builder()
                        .user(user)
                        .fullName(fullName)
                        .status(WorkerStatus.DRAFT)
                        .isVisible(false)
                        .isOnline(false)
                        .build();
                workerProfileRepository.save(workerProfile);
            }
        } else if (role == UserRole.CLIENT) {
            if (workerOpt.isPresent()) {
                workerProfileRepository.delete(workerOpt.get());
                workerProfileRepository.flush();
            }
            if (clientOpt.isPresent()) {
                ClientProfile clientProfile = clientOpt.get();
                clientProfile.setFullName(fullName);
                clientProfileRepository.save(clientProfile);
            } else {
                ClientProfile clientProfile = ClientProfile.builder()
                        .user(user)
                        .fullName(fullName)
                        .build();
                clientProfileRepository.save(clientProfile);
            }
        }
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        Auth auth = authRepository.findByUserEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("Invalid email or password.", HttpStatus.UNAUTHORIZED));

        if (!auth.isActive()) {
            throw new com.kazikonnect.backend.features.auth.AuthException("Account is suspended. Please contact support.", HttpStatus.FORBIDDEN);
        }

        if (!Boolean.TRUE.equals(auth.getEmailVerified())) {
            throw new com.kazikonnect.backend.features.auth.AuthException("Email not verified. Please verify your email before logging in.", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(password, auth.getPasswordHash())) {
            throw new com.kazikonnect.backend.features.auth.AuthException("Invalid email or password.", HttpStatus.UNAUTHORIZED);
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

    private String normalizeEmail(String email) {
        return Optional.ofNullable(email)
                .map(String::trim)
                .map(str -> str.toLowerCase(Locale.ROOT))
                .filter(str -> !str.isBlank())
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("Email is required.", HttpStatus.BAD_REQUEST));
    }

    @Transactional
    public void initializePasswordReset(String email) {
        String normalizedEmail = normalizeEmail(email);

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            String resetToken = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);
            LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(15);

            passwordResetTokenRepository.deleteAllByUser(user);
            PasswordResetToken token = PasswordResetToken.builder()
                    .token(resetToken)
                    .user(user)
                    .expiryDate(expiryDate)
                    .build();
            passwordResetTokenRepository.save(token);

            emailService.sendPasswordResetEmail(normalizedEmail, resetToken);
        });
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new com.kazikonnect.backend.features.auth.AuthException("Password must be at least 8 characters long.", HttpStatus.BAD_REQUEST);
        }

        PasswordResetToken tokenData = passwordResetTokenRepository.findByToken(resetToken)
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("The verification code is invalid or has expired.", HttpStatus.BAD_REQUEST));

        if (LocalDateTime.now().isAfter(tokenData.getExpiryDate())) {
            passwordResetTokenRepository.delete(tokenData);
            throw new com.kazikonnect.backend.features.auth.AuthException("This verification code has expired. Please request a new verification code.", HttpStatus.BAD_REQUEST);
        }

        User user = tokenData.getUser();

        Auth auth = authRepository.findByUserId(user.getId())
            .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("Unable to update the password at this time. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR));

        auth.setPasswordHash(passwordEncoder.encode(newPassword));
        authRepository.save(auth);

        passwordResetTokenRepository.delete(tokenData);
    }

    @Transactional
    public void verifyEmail(String verificationToken) {
        log.info("Verifying email token: {}", verificationToken);
        Auth auth = authRepository.findByEmailVerificationToken(verificationToken)
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("The verification code is invalid or has already been used.", HttpStatus.BAD_REQUEST));

        if (auth.getEmailVerificationTokenExpiry() != null && 
            LocalDateTime.now().isAfter(auth.getEmailVerificationTokenExpiry())) {
            log.info("Verification token expired for auth id {} at {}", auth.getId(), auth.getEmailVerificationTokenExpiry());
            throw new com.kazikonnect.backend.features.auth.AuthException("This verification code has expired. Please request a new verification code.", HttpStatus.BAD_REQUEST);
        }

        log.info("Email verification token valid for auth id {}", auth.getId());
        auth.setEmailVerified(true);
        auth.setEmailVerificationToken(null);
        auth.setEmailVerificationTokenExpiry(null);
        authRepository.save(auth);
    }

    @Transactional
    public void verifyEmail(String email, String verificationToken) {
        log.info("Verifying email: {} with token: {}", email, verificationToken);
        String normalizedEmail = normalizeEmail(email);
        Auth auth = authRepository.findByUserEmailIgnoreCaseAndEmailVerificationToken(normalizedEmail, verificationToken)
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("The verification code is invalid or has already been used.", HttpStatus.BAD_REQUEST));

        if (auth.getEmailVerificationTokenExpiry() != null && 
            LocalDateTime.now().isAfter(auth.getEmailVerificationTokenExpiry())) {
            log.info("Verification token expired for auth id {} at {}", auth.getId(), auth.getEmailVerificationTokenExpiry());
            throw new com.kazikonnect.backend.features.auth.AuthException("This verification code has expired. Please request a new verification code.", HttpStatus.BAD_REQUEST);
        }

        log.info("Email verification token valid for auth id {}", auth.getId());
        auth.setEmailVerified(true);
        auth.setEmailVerificationToken(null);
        auth.setEmailVerificationTokenExpiry(null);
        authRepository.save(auth);
    }

    @Transactional
    public String resendEmailVerification(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("No account was found for that email.", HttpStatus.BAD_REQUEST));

        Auth auth = authRepository.findByUserId(user.getId())
                .orElseThrow(() -> new com.kazikonnect.backend.features.auth.AuthException("Unable to resend verification. Please contact support.", HttpStatus.BAD_REQUEST));

        if (Boolean.TRUE.equals(auth.getEmailVerified())) {
            return "Email is already verified. Please log in.";
        }

        String verificationToken = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);
        auth.setEmailVerificationToken(verificationToken);
        auth.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        authRepository.save(auth);
        authRepository.flush();

        emailService.sendEmailVerificationEmail(normalizedEmail, user.getFullName(), verificationToken);
        return "A new verification code has been sent to " + normalizedEmail + ". Please check your inbox.";
    }

}


record AuthResponse(String accessToken, String refreshToken, String userId, String username, String email, String name, String role, String profilePictureUrl) {}
