package com.kazikonnect.backend.features.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Registers a new Client or Worker account")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        String message = authService.register(
                request.username(),
                request.email(),
                request.password(),
                request.firstName(),
                request.secondName(),
                request.role()
        );
        return ResponseEntity.ok(new ApiResponse<>(true, message, null));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Logs in a user and returns a JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new ApiResponse<>(true, "Login successful.", response));
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request password reset", description = "Sends a password reset token to the user's email if the account exists")
    public ResponseEntity<ApiResponse<String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.initializePasswordReset(request.email());
        return ResponseEntity.ok(new ApiResponse<>(true, "If an account exists for this email, a password reset link has been sent.", null));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Confirm password reset", description = "Resets password with valid reset token")
    public ResponseEntity<ApiResponse<String>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new ApiResponse<>(true, "Your password has been updated successfully. Please log in with your new password.", null));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link", description = "Sends a fresh verification email when the original link expired or was lost")
    public ResponseEntity<ApiResponse<String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        String message = authService.resendEmailVerification(request.email());
        return ResponseEntity.ok(new ApiResponse<>(true, message, null));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verifies the user's email using the verification token/OTP")
    public ResponseEntity<ApiResponse<String>> verifyEmail(
            @RequestParam(required = false) String email,
            @RequestParam String token) {
        log.info("Verify-email request received for email: {} with token: {}", email, token);
        if (email != null && !email.isBlank()) {
            authService.verifyEmail(email, token);
        } else {
            authService.verifyEmail(token);
        }
        log.info("Verify-email succeeded for email: {} with token: {}", email, token);
        return ResponseEntity.ok(new ApiResponse<>(true, "Email verified successfully. You can now log in.", null));
    }

}

record RegisterRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    String username,
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,
    
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be 2-50 characters")
    String firstName,
    
    @NotBlank(message = "Second name is required")
    @Size(min = 2, max = 50, message = "Second name must be 2-50 characters")
    String secondName,
    
    @NotNull(message = "Role is required")
    UserRole role
) {}

record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,
    
    @NotBlank(message = "Password is required")
    String password
) {}


@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
record PasswordResetRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email
) {}

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
record PasswordResetConfirm(
    @NotBlank(message = "Token is required")
    String token,
    
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String newPassword
) {}

record ResendVerificationRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email
) {}
