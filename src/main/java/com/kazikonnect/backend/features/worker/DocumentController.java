package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.core.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;

    // CREATE: Worker uploads a document to Cloudinary (using userId)
    @PostMapping
    @PreAuthorize("hasAuthority('Worker')")
    public ResponseEntity<?> uploadDocument(
            @RequestParam UUID userId,
            @RequestParam String type,
            @RequestParam String name,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        
        try {
            var actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");
            if (actor.getRole() != UserRole.ADMIN && !actor.getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Cannot upload documents for another worker.");
            }

            WorkerProfile worker = workerProfileRepository.findByUserId(userId).orElse(null);
            if (worker == null) {
                return ResponseEntity.badRequest().body("Worker Profile not found for user ID: " + userId);
            }

            // Upload to Cloudinary
            Map<?, ?> uploadResult = cloudinaryService.upload(file, "Kazi Konnect/documents/" + userId);
            String fileUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            Document document = new Document();
            document.setWorker(worker);
            document.setType(type);
            document.setName(name);
            document.setDocumentUrl(fileUrl);
            document.setPublicId(publicId);
            
            Document saved = documentRepository.save(document);
            return ResponseEntity.ok(DocumentDTO.from(saved));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to upload document: " + e.getMessage());
        }
    }

    // READ: Get all documents for a worker (using userId)
    @GetMapping("/worker/user/{userId}")
    @PreAuthorize("hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> getWorkerDocuments(@PathVariable UUID userId, Principal principal) {
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");
        if (actor.getRole() != UserRole.ADMIN && !actor.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }

        WorkerProfile worker = workerProfileRepository.findByUserId(userId).orElse(null);
        if (worker == null) return ResponseEntity.ok(List.of());
        
        List<DocumentDTO> list = documentRepository.findAllByWorkerId(worker.getId()).stream()
                .map(DocumentDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // UPDATE: Admin verifies a document
    @PutMapping("/{documentId}/verify")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> verifyDocument(@PathVariable UUID documentId, @RequestParam UUID adminId) {
        return documentRepository.findById(documentId).map(doc -> {
            doc.setVerifiedBy(adminId);
            doc.setVerifiedAt(LocalDateTime.now());
            return ResponseEntity.ok(DocumentDTO.from(documentRepository.save(doc)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Delete a document
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> deleteDocument(@PathVariable UUID documentId, Principal principal) {
        return documentRepository.findById(documentId).map(doc -> {
            var user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) return ResponseEntity.status(401).build();

            boolean isAdmin = user.getRole() == UserRole.ADMIN;
            boolean isOwner = doc.getWorker() != null && doc.getWorker().getUser() != null && 
                             doc.getWorker().getUser().getId().toString().equals(user.getId().toString());

            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden: You do not own this document"));
            }

            documentRepository.delete(doc);
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
        }).orElse(ResponseEntity.ok(Map.of("message", "Document already deleted")));
    }
}
