package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEvidenceDTO {
    private String fileName;
    private String fileUrl;
    private String fileType;  // 'screenshot', 'photo', 'video', 'pdf', 'contract', 'receipt', 'other'
    private Long fileSizeBytes;
    private String mimeType;
    private String description;
}
