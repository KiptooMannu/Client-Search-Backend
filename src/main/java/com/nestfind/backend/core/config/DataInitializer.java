package com.nestfind.backend.core.config;

import com.nestfind.backend.features.auth.*;
import com.nestfind.backend.features.client.*;
import com.nestfind.backend.features.worker.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DataInitializer implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final String PASS = "password123";

    @Override
    public void run(String... args) throws Exception {
        // Drop the legacy check constraint if it exists to allow the DRAFT status
        try {
            jdbcTemplate.execute("ALTER TABLE worker_profiles DROP CONSTRAINT IF EXISTS worker_profiles_status_check");
            log.info("Successfully dropped legacy worker_profiles_status_check constraint.");
        } catch (Exception e) {
            log.warn("Could not drop constraint (it might already be gone): {}", e.getMessage());
        }

        if (userRepository.existsByEmail("admin@nestfind.com")) {
            log.info("Database already seeded. Skipping initialization.");
            return;
        }

        log.info("--- Starting Database Seeding ---");
        // cleanup(); // Disabled after clean run to maintain ID stability
        seedSkills();
        seedAdmin();
        seedNamedWorkers();
        seedNamedClients();
        seedBulkWorkers();
        seedBulkClients();
        log.info("--- Database Seeding Complete ---");
    }

    // ─── 1. Skills ──────────────────────────────────────────────────────────────
    private void seedSkills() {
        if (skillRepository.count() > 0) return;
        List<String> names = List.of(
            "Plumbing", "Electrical Wiring", "Carpentry", "Masonry",
            "Painting", "HVAC Installation", "Interior Design", "Roofing",
            "Landscape Architecture", "Welding", "Tiling", "General Repairs"
        );
        names.forEach(n -> skillRepository.save(Skill.builder().name(n).build()));
        log.info("Seeded {} skills.", names.size());
    }

    // ─── 2. Admin ────────────────────────────────────────────────────────────────
    private void seedAdmin() {
        if (userRepository.existsByEmail("admin@nestfind.com")) return;
        User u = userRepository.save(User.builder()
            .username("system_admin").email("admin@nestfind.com")
            .fullName("System Administrator").role(UserRole.ADMIN).build());
        authRepository.save(Auth.builder().user(u)
            .passwordHash(passwordEncoder.encode("admin123")).isActive(true).build());
        log.info("Seeded admin: admin@nestfind.com / admin123");
    }

    // ─── 3. Named Workers (rich data) ────────────────────────────────────────────
    private void seedNamedWorkers() {
        // Removed dummy workers to provide a clean slate
    }

    // ─── 4. Named Clients (rich data) ────────────────────────────────────────────
    private void seedNamedClients() {
        createClient("emmanuel_client", "client@user.com", "Emmanuel Otieno",
            "Emmanuel Otieno", "0788990011");
        createClient("linda_client", "linda@client.com", "Linda Wanjiku",
            "Linda Wanjiku", "0799001122");
        createClient("james_client", "james@client.com", "James Mwangi",
            "James Mwangi", "0700112233");
        createClient("amina_client", "amina@client.com", "Amina Hassan",
            "Amina Hassan", "0711223300");
    }

    private void createClient(String username, String email, String fullName,
                               String profileName, String phone) {
        if (userRepository.existsByEmail(email)) return;
        User u = userRepository.save(User.builder()
            .username(username).email(email).fullName(fullName)
            .role(UserRole.CLIENT).build());
        authRepository.save(Auth.builder().user(u)
            .passwordHash(passwordEncoder.encode(PASS)).isActive(true).build());
        clientProfileRepository.save(ClientProfile.builder()
            .user(u).fullName(profileName).phoneNumber(phone).build());
        log.info("Seeded client: {}", email);
    }

    private void seedBulkWorkers() {
        // Removed bulk dummy data for a cleaner experience
    }

    private void seedBulkClients() {
        if (clientProfileRepository.count() >= 10) return;
        log.info("--- Seeding bulk clients ---");
        String[] firstNames = {"Wanjiku", "Omondi", "Adhiambo", "Njeru", "Kiprotich",
                               "Achieng", "Mugo", "Waithera", "Barasa", "Cherono"};
        String[] lastNames  = {"Kariuki", "Otieno", "Auma", "Mwenda", "Koech",
                               "Awuor", "Githae", "Njoki", "Simiyu", "Rono"};
        for (int i = 0; i < 20; i++) {
            String email = "client" + i + "@bulk.com";
            if (userRepository.existsByEmail(email)) continue;
            String fn = firstNames[i % firstNames.length];
            String ln = lastNames[i % lastNames.length];
            String name = fn + " " + ln;
            User u = userRepository.save(User.builder()
                .username("client_bulk_" + i).email(email)
                .fullName(name).role(UserRole.CLIENT).build());
            authRepository.save(Auth.builder().user(u)
                .passwordHash(passwordEncoder.encode(PASS)).isActive(true).build());
            clientProfileRepository.save(ClientProfile.builder()
                .user(u).fullName(name)
                .phoneNumber("07" + String.format("%08d", 1000 + i)).build());
        }
        log.info("Seeded bulk clients.");
    }
}
