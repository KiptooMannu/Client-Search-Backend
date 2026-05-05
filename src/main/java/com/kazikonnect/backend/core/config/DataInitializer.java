package com.kazikonnect.backend.core.config;

import com.kazikonnect.backend.features.auth.*;
import com.kazikonnect.backend.features.client.*;
import com.kazikonnect.backend.features.worker.*;
import com.kazikonnect.backend.features.common.*;
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
    private final WorkerProfileRepository workerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.kazikonnect.backend.features.common.MessageRepository messageRepository;
    private final com.kazikonnect.backend.features.common.NotificationRepository notificationRepository;
    private final com.kazikonnect.backend.features.worker.JobRequestRepository jobRequestRepository;
    private final com.kazikonnect.backend.features.worker.ReviewRepository reviewRepository;

    private static final String PASS = "password123";

    @Override
    public void run(String... args) throws Exception {

        try {
            jdbcTemplate.execute("ALTER TABLE worker_profiles DROP CONSTRAINT IF EXISTS worker_profiles_status_check");
            log.info("Successfully dropped legacy worker_profiles_status_check constraint.");
        } catch (Exception e) {
            log.warn("Could not drop constraint (it might already be gone): {}", e.getMessage());
        }

        log.info("--- Starting Database Seeding ---");
        cleanup();
        seedSkills();
        seedAdmin();
        seedNamedWorkers();
        seedNamedClients();
        seedBulkWorkers();
        seedBulkClients();
        seedInteractions();
        log.info("--- Database Seeding Complete ---");
    }

    private void seedInteractions() {
        if (messageRepository.count() > 5)
            return;
        log.info("--- Seeding Interlinked Interactions (Jobs & Reviews) ---");

        List<User> clients = userRepository.findAll().stream()
                .filter(u -> UserRole.CLIENT.equals(u.getRole()))
                .limit(30)
                .toList();
        List<WorkerProfile> workers = workerProfileRepository.findAll().stream()
                .limit(30)
                .toList();

        for (int i = 0; i < Math.min(clients.size(), workers.size()); i++) {
            User client = clients.get(i);
            WorkerProfile worker = workers.get(i);

            // 1. Job Request
            JobRequest job = JobRequest.builder()
                    .client(client)
                    .worker(worker)
                    .description("Standard service request for " + worker.getCategory())
                    .status(i % 2 == 0 ? JobStatus.COMPLETED : JobStatus.PENDING)
                    .build();
            jobRequestRepository.save(job);

            // 2. Review (only for completed jobs)
            if (job.getStatus() == JobStatus.COMPLETED) {
                reviewRepository.save(Review.builder()
                        .client(client)
                        .worker(worker)
                        .rating(4 + (i % 2))
                        .comment("Excellent work on the " + worker.getCategory() + " project. " + worker.getFullName()
                                + " was professional and efficient.")
                        .build());
            }

            // 3. Message
            messageRepository.save(Message.builder()
                    .sender(client)
                    .receiver(worker.getUser())
                    .content("Hi, I'm interested in your " + worker.getCategory() + " expertise.")
                    .build());

            // 4. Notification
            notificationRepository.save(Notification.builder()
                    .user(worker.getUser())
                    .title("New Activity")
                    .message(client.getFullName() + " interacted with your profile.")
                    .type("INFO")
                    .build());
        }
    }

    // ─── 1. Skills ──────────────────────────────────────────────────────────────
    private void seedSkills() {
        if (skillRepository.count() > 0)
            return;
        List<String> names = List.of(
                "Plumbing", "Electrical Wiring", "Carpentry", "Masonry",
                "Painting", "HVAC Installation", "Interior Design", "Roofing",
                "Landscape Architecture", "Welding", "Tiling", "General Repairs");
        names.forEach(n -> skillRepository.save(Skill.builder().name(n).build()));
        log.info("Seeded {} skills.", names.size());
    }

    // ─── 2. Admin ────────────────────────────────────────────────────────────────
    private void seedAdmin() {
        if (userRepository.existsByEmail("admin@kazikonnect.com") ||
                userRepository.existsByUsername("system_admin"))
            return;
        User u = userRepository.save(User.builder()
                .username("system_admin").email("admin@kazikonnect.com")
                .fullName("System Administrator").role(UserRole.ADMIN).build());
        authRepository.save(Auth.builder().user(u)
                .passwordHash(passwordEncoder.encode("admin123")).isActive(true).build());
        log.info("Seeded admin: admin@kazikonnect.com / admin123");
    }

    // ─── 3. Named Workers (rich data) ────────────────────────────────────────────
    private void seedNamedWorkers() {
        createWorker("sarah_design", "sarah@design.com", "Sarah Johnson", "Interior Design", 25.0,
                "https://images.unsplash.com/photo-1594744803329-a584af1dd51a?q=80&w=2000&auto=format&fit=crop",
                "Transforming spacomces with elegant and functional designs for over 8 years.");
        createWorker("david_electric", "david@power.com", "David Chen", "Electrical Wiring", 30.0,
                "https://images.unsplash.com/photo-1560250097-0b93528c311a?q=80&w=2000&auto=format&fit=crop",
                "Expert electrician specializing in smart home integration and safety audits.");
        createWorker("mike_plumb", "mike@pipes.com", "Michael Smith", "Plumbing", 28.0,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=2000&auto=format&fit=crop",
                "Reliable plumbing solutions for residential and commercial properties.");
    }

    private void createWorker(String username, String email, String fullName, String category, double rate, String img,
            String bio) {
        if (userRepository.existsByEmail(email))
            return;

        User u = userRepository.save(User.builder()
                .username(username).email(email).fullName(fullName)
                .role(UserRole.WORKER).build());

        authRepository.save(Auth.builder().user(u)
                .passwordHash(passwordEncoder.encode(PASS)).isActive(true).build());

        workerProfileRepository.save(WorkerProfile.builder()
                .user(u)
                .fullName(fullName)
                .category(category)
                .hourlyRate(rate)
                .profilePictureUrl(img)
                .bio(bio)
                .status(WorkerStatus.APPROVED)
                .isVisible(true)
                .isOnline(true)
                .location("Nairobi, Kenya")
                .experienceYears(5)
                .build());

        log.info("Seeded worker: {}", email);
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
        if (userRepository.existsByEmail(email))
            return;
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
        if (workerProfileRepository.count() >= 30)
            return;
        log.info("--- Seeding 30 Professionals ---");
        String[] categories = { "Plumbing", "Electrical Wiring", "Carpentry", "Masonry", "Painting", "Interior Design",
                "General Repairs" };
        for (int i = 1; i <= 30; i++) {
            String email = "worker" + i + "@kazikonnect.com";
            createWorker("pro_user_" + i, email, "Professional Worker " + i,
                    categories[i % categories.length], 20.0 + (i % 15),
                    "https://images.unsplash.com/photo-1540553016722-983e48a2cd10?q=80&w=2000&auto=format&fit=crop",
                    "Dedicated professional with years of experience in " + categories[i % categories.length] + ".");
        }
    }

    private void seedBulkClients() {
        if (clientProfileRepository.count() >= 30)
            return;
        log.info("--- Seeding 30 Clients ---");
        for (int i = 1; i <= 30; i++) {
            createClient("client_user_" + i, "client" + i + "@kazikonnect.com", "Client " + i,
                    "Client Profile " + i, "07" + String.format("%08d", 2000 + i));
        }
    }

    private void cleanup() {
        log.info("Cleaning up database (Fast SQL Wipe)...");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE messages, notifications, reviews, job_requests, auth, client_profiles, worker_profiles, users, skills RESTART IDENTITY CASCADE");
            log.info("Database wiped successfully.");
        } catch (Exception e) {
            log.warn("Standard truncate failed, trying repository delete: {}", e.getMessage());
            messageRepository.deleteAll();
            notificationRepository.deleteAll();
            reviewRepository.deleteAll();
            jobRequestRepository.deleteAll();
            authRepository.deleteAll();
            clientProfileRepository.deleteAll();
            workerProfileRepository.deleteAll();
            userRepository.deleteAll();
            skillRepository.deleteAll();
        }
    }
}
