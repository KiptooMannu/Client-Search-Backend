package com.nestfind.backend.features.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Registers a new Client, Worker, or Admin account")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Verification Marker: Calling register with 5 args
        try {
            String u = request.username();
            String e = request.email();
            String p = request.password();
            String n = request.name();
            UserRole r = request.role();
            
            String message = authService.register(u, e, p, n, r);
            return ResponseEntity.ok(message);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Logs in a user and returns a JWT token")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request.email(), request.password()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}

record RegisterRequest(String username, String email, String password, String name, UserRole role) {}
record LoginRequest(String email, String password) {}
