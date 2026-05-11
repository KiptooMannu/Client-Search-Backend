package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.core.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            Map<?, ?> uploadResult = cloudinaryService.upload(file, "Kazi Konnect/media");
            return ResponseEntity.ok(Map.of(
                "url", uploadResult.get("secure_url"),
                "publicId", uploadResult.get("public_id"),
                "format", uploadResult.get("format"),
                "size", uploadResult.get("bytes")
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to upload media: " + e.getMessage());
        }
    }
}
