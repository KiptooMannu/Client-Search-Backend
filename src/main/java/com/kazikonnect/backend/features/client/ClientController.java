package com.kazikonnect.backend.features.client;

import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.security.Principal;

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
    public ResponseEntity<?> createProfile(@RequestBody ClientProfile profile, @RequestParam String email, Principal principal) {
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (!actor.getEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(403).body("Forbidden: email must match authenticated client.");
        }
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
    public ResponseEntity<?> getProfile(@PathVariable UUID userId, Principal principal) {
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (actor.getRole() != com.kazikonnect.backend.features.auth.UserRole.ADMIN && !actor.getId().equals(userId)) {
            return ResponseEntity.status(403).body("Forbidden: cannot access another client profile.");
        }
        return clientProfileRepository.findByUserId(userId)
                .map(p -> ResponseEntity.ok(ClientProfileDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Client edits their profile
    @PutMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Update client profile", description = "Updates an existing client profile with new details")
    public ResponseEntity<?> updateProfile(@PathVariable UUID profileId, @RequestBody ClientProfile updates, Principal principal) {
        return clientProfileRepository.findById(profileId).map(existing -> {
            var actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            if (existing.getUser() == null || !existing.getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: cannot edit another client profile.");
            }
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
    public ResponseEntity<?> deleteProfile(@PathVariable UUID profileId, Principal principal) {
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return clientProfileRepository.findById(profileId).map(existing -> {
            boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
            boolean owner = existing.getUser() != null && existing.getUser().getId().equals(actor.getId());
            if (!admin && !owner) {
                return ResponseEntity.status(403).body("Forbidden: cannot delete another client profile.");
            }
            clientProfileRepository.deleteById(profileId);
            return ResponseEntity.ok("Profile deleted successfully.");
        }).orElse(ResponseEntity.notFound().build());
    }
}
