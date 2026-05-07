package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.core.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
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

    // CREATE: Worker uploads a document to Cloudinary
    @PostMapping
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> uploadDocument(
            @RequestParam UUID workerProfileId,
            @RequestParam String type,
            @RequestParam String name,
            @RequestParam("file") MultipartFile file) {
        
        try {
            WorkerProfile worker = workerProfileRepository.findById(workerProfileId).orElse(null);
            if (worker == null) {
                return ResponseEntity.badRequest().body("Worker Profile not found.");
            }

            // Upload to Cloudinary
            Map<?, ?> uploadResult = cloudinaryService.upload(file, "Kazi Konnect/documents/" + workerProfileId);
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

    // READ: Get all documents for a worker
    @GetMapping("/worker/{workerProfileId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public List<DocumentDTO> getWorkerDocuments(@PathVariable UUID workerProfileId) {
        return documentRepository.findAllByWorkerId(workerProfileId).stream()
                .map(DocumentDTO::from)
                .collect(Collectors.toList());
    }

    // UPDATE: Admin verifies a document
    @PutMapping("/{documentId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verifyDocument(@PathVariable UUID documentId, @RequestParam UUID adminId) {
        return documentRepository.findById(documentId).map(doc -> {
            doc.setVerifiedBy(adminId);
            doc.setVerifiedAt(LocalDateTime.now());
            return ResponseEntity.ok(DocumentDTO.from(documentRepository.save(doc)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Delete a document
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteDocument(@PathVariable UUID documentId, java.security.Principal principal) {
        return documentRepository.findById(documentId).map(doc -> {
            var user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) return ResponseEntity.status(401).build();

            boolean isAdmin = user.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
            boolean isOwner = doc.getWorker() != null && doc.getWorker().getUser().getId().equals(user.getId());

            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).body(java.util.Map.of("error", "Forbidden: You do not own this document"));
            }

            documentRepository.delete(doc);
            return ResponseEntity.ok(java.util.Map.of("message", "Document deleted successfully"));
        }).orElse(ResponseEntity.ok(java.util.Map.of("message", "Document already deleted")));
    }
}
