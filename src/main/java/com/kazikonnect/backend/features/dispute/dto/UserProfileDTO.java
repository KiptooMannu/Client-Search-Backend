package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private UUID id;
    private String fullName;
    private String email;
    private String role;
    private String profilePictureUrl;
    private String bio;
    private String location;
    private Double rating;
    private Integer completedJobs;
    private Boolean isVerified;
    private Boolean isOnline;
}
