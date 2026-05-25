package com.kazikonnect.backend.features.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

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
