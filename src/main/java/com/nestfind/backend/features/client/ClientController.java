package com.nestfind.backend.features.client;

import com.nestfind.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@SuppressWarnings("null")
@Tag(name = "Client Management", description = "Endpoints for managing client profiles")
public class ClientController {

    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;

    // CREATE: Submit a new client profile
    @PostMapping("/profile")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Create a new client profile", description = "Creates a profile associated with a client user account")
    public ResponseEntity<?> createProfile(@RequestBody ClientProfile profile, @RequestParam String email) {
        if (clientProfileRepository.findByUserEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body("Error: A profile already exists for this email.");
        }
        var user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().build();
        }
        profile.setUser(user);
        return ResponseEntity.ok(ClientProfileDTO.from(clientProfileRepository.save(profile)));
    }

    // READ: Get a single client profile by userId
    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    @Operation(summary = "Get client profile", description = "Retrieves a client profile by the user's UUID")
    public ResponseEntity<?> getProfile(@PathVariable UUID userId) {
        return clientProfileRepository.findByUserId(userId)
                .map(p -> ResponseEntity.ok(ClientProfileDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Client edits their profile
    @PutMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Update client profile", description = "Updates an existing client profile with new details")
    public ResponseEntity<?> updateProfile(@PathVariable UUID profileId, @RequestBody ClientProfile updates) {
        return clientProfileRepository.findById(profileId).map(existing -> {
            if (updates.getFullName() != null)
                existing.setFullName(updates.getFullName());
            if (updates.getPhoneNumber() != null)
                existing.setPhoneNumber(updates.getPhoneNumber());
            return ResponseEntity.ok(ClientProfileDTO.from(clientProfileRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Client deletes their own profile
    @DeleteMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    @Operation(summary = "Delete client profile", description = "Deletes a client profile by profile ID")
    public ResponseEntity<?> deleteProfile(@PathVariable UUID profileId) {
        if (!clientProfileRepository.existsById(profileId)) {
            return ResponseEntity.notFound().build();
        }
        clientProfileRepository.deleteById(profileId);
        return ResponseEntity.ok("Profile deleted successfully.");
    }
}
