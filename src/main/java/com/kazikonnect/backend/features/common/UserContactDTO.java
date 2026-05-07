package com.kazikonnect.backend.features.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDTO {
    private UUID id;
    private String username;
    private String email;
    private String role;
}
