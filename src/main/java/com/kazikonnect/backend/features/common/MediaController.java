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
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String folder) {
        try {
            String uploadFolder = folder != null && !folder.isBlank() ? folder : "Kazi Konnect/media";
            Map<?, ?> uploadResult = cloudinaryService.upload(file, uploadFolder);
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("url", uploadResult.get("secure_url"));
            response.put("publicId", uploadResult.get("public_id"));
            response.put("format", uploadResult.get("format"));
            response.put("size", uploadResult.get("bytes"));
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to upload media: " + e.getMessage());
        }
    }
}
