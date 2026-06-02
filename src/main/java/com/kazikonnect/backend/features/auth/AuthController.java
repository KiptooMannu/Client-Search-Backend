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
    private final UserRepository userRepository;
    private final AuthRepository authRepository;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Registers a new Client, Worker, or Admin account")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            String u = request.username();
            String e = request.email();
            String p = request.password();
            String f = request.firstName();
            String l = request.secondName();
            UserRole r = request.role();
            
            String message = authService.register(u, e, p, f, l, r);
            return ResponseEntity.ok(message);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Logs in a user and returns a JWT token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request.email(), request.password()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request password reset", description = "Sends a password reset token to the user's email if the account exists")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        try {
            authService.initializePasswordReset(request.email());
            return ResponseEntity.ok("If an account exists for this email, a password reset link has been sent.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Confirm password reset", description = "Resets password with valid reset token")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        try {
            authService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok("Password reset successful. Please log in with your new password.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link", description = "Sends a fresh verification email when the original link expired or was lost")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        try {
            String message = authService.resendEmailVerification(request.email());
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verifies the user's email using the verification token from the email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        log.info("Verify-email request received with token: {}", token);
        try {
            authService.verifyEmail(token);
            log.info("Verify-email succeeded for token: {}", token);
            return ResponseEntity.ok("Email verified successfully. You can now log in.");
        } catch (Exception e) {
            log.warn("Verify-email failed for token: {} with error: {}", token, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/debug/verification-token/{email}")
    @Operation(summary = "DEBUG: Get verification token by email", description = "DEV ONLY: Returns the verification token for an unverified user (for testing)")
    public ResponseEntity<?> getVerificationToken(@PathVariable String email) {
        try {
            var user = userRepository.findByEmail(email.toLowerCase()).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            var auth = authRepository.findByUserId(user.getId()).orElse(null);
            if (auth == null) {
                return ResponseEntity.badRequest().body("Auth record not found");
            }
            
            if (auth.isEmailVerified()) {
                return ResponseEntity.ok(new java.util.HashMap<String, String>() {{
                    put("message", "Email is already verified");
                    put("verified", "true");
                }});
            }
            
            String token = auth.getEmailVerificationToken();
            if (token == null) {
                return ResponseEntity.badRequest().body("No verification token found");
            }
            
            String verificationLink = "http://localhost:4200/verify-email?token=" + token;
            return ResponseEntity.ok(new java.util.HashMap<String, String>() {{
                put("token", token);
                put("link", verificationLink);
                put("email", email);
            }});
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/debug/create-test-user")
    @Operation(summary = "DEBUG: Create test user", description = "DEV ONLY: Creates a test user and returns its verification token and link")
    public ResponseEntity<?> createTestUser(@RequestParam String email) {
        try {
            String normalized = email.toLowerCase().trim();
            String username = normalized.split("@")[0] + System.currentTimeMillis() % 10000;
            // Minimal test data
            String firstName = "Rotich";
            String secondName = "Test";
            String password = "TempPass123!";

            // Register user (will throw if validation fails)
            String message = authService.register(username, normalized, password, firstName, secondName, UserRole.CLIENT);

            // Lookup token
            var user = userRepository.findByEmail(normalized).orElse(null);
            if (user == null) return ResponseEntity.badRequest().body("User not found after registration");
            var auth = authRepository.findByUserId(user.getId()).orElse(null);
            if (auth == null) return ResponseEntity.badRequest().body("Auth record not found after registration");
            String token = auth.getEmailVerificationToken();
            String verificationLink = "http://localhost:4200/verify-email?token=" + token;

            return ResponseEntity.ok(new java.util.HashMap<String, String>() {{
                put("message", message);
                put("token", token == null ? "" : token);
                put("link", token == null ? "" : verificationLink);
            }});
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
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
