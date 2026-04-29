package com.nestfind.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nestfind.backend.features.auth.Auth;
import com.nestfind.backend.features.auth.User;
import com.nestfind.backend.features.auth.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class RecursionTest {

    @Test
    public void testSerialization() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("test_user")
                .email("test@test.com")
                .role(UserRole.WORKER)
                .build();

        Auth auth = Auth.builder()
                .id(UUID.randomUUID())
                .user(user)
                .passwordHash("hash")
                .build();

        user.setAuth(auth);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(user);
        System.out.println("--- Serialized User JSON ---");
        System.out.println(json);
        
        if (json.contains("auth") || json.contains("passwordHash")) {
             System.out.println("ERROR: Auth was serialized inside User!");
        } else {
             System.out.println("SUCCESS: Auth was ignored in User.");
        }

        // Test WorkerProfile
        com.nestfind.backend.features.worker.WorkerProfile profile = com.nestfind.backend.features.worker.WorkerProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .fullName("Kevin Pro")
                .status(com.nestfind.backend.features.worker.WorkerStatus.PENDING)
                .build();
        user.setWorkerProfile(profile);

        String profileJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile);
        System.out.println("--- Serialized WorkerProfile JSON ---");
        System.out.println(profileJson);

        if (profileJson.contains("\"user\" : {")) {
            System.out.println("NOTE: WorkerProfile contains User object (Expected if not ignored)");
        }
        
        // Test WorkerProfileDTO
        com.nestfind.backend.features.worker.WorkerProfileDTO dto = com.nestfind.backend.features.worker.WorkerProfileDTO.from(profile);
        String dtoJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dto);
        System.out.println("--- Serialized WorkerProfileDTO JSON ---");
        System.out.println(dtoJson);

        if (dtoJson.contains("\"user\" : {")) {
             System.out.println("ERROR: WorkerProfileDTO contains User object!");
        } else {
             System.out.println("SUCCESS: WorkerProfileDTO is flat.");
        }
    }
}
