package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.Auth;
import com.kazikonnect.backend.features.auth.AuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SettingsController {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    // UPDATE: Update profile name
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> updates, Principal principal) {
        return userRepository.findByUsername(principal.getName()).map(user -> {
            String newName = updates.get("name");
            if (newName != null && !newName.isBlank()) {
                user.setFullName(newName);
                // Also update firstName/secondName if they exist to keep consistency
                String[] parts = newName.split(" ", 2);
                if (parts.length > 0) user.setFirstName(parts[0]);
                if (parts.length > 1) user.setSecondName(parts[1]);
                
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("message", "Profile updated successfully", "name", user.getFullName()));
            }
            return ResponseEntity.badRequest().body("Name cannot be empty.");
        }).orElse(ResponseEntity.status(401).build());
    }

    // UPDATE: Update password
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updatePassword(@RequestBody Map<String, String> passwords, Principal principal) {
        return userRepository.findByUsername(principal.getName()).map(user -> {
            String newPassword = passwords.get("newPassword");
            if (newPassword == null || newPassword.length() < 8) {
                return ResponseEntity.badRequest().body("Password must be at least 8 characters long.");
            }
            
            Auth auth = authRepository.findByUserId(user.getId()).orElse(null);
            if (auth == null) {
                return ResponseEntity.status(404).body("Auth record not found.");
            }
            
            auth.setPasswordHash(passwordEncoder.encode(newPassword));
            authRepository.save(auth);
            
            return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
        }).orElse(ResponseEntity.status(401).build());
    }

    // DELETE: Liquidate account (Close Profile)
    @DeleteMapping("/account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteAccount(Principal principal) {
        return userRepository.findByUsername(principal.getName()).map(user -> {
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("message", "Account liquidated successfully. We're sorry to see you go."));
        }).orElse(ResponseEntity.status(401).build());
    }
}
