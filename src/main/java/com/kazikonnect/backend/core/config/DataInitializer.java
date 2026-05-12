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
import java.util.stream.Collectors;

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
    private final DocumentRepository documentRepository;

    private static final String PASS = "password123";

    @Override
    public void run(String... args) throws Exception {
        // Drop the status check constraint to allow new statuses (CANCELLED, IN_PROGRESS)
        try {
            jdbcTemplate.execute("ALTER TABLE job_requests DROP CONSTRAINT IF EXISTS job_requests_status_check");
            log.info("Dropped job_requests_status_check constraint for compatibility.");
        } catch (Exception e) {
            log.warn("Could not drop constraint: " + e.getMessage());
        }

        boolean shouldSeed = true; // Enabled for verification
        
        if (shouldSeed) {
            log.info("--- Starting Database Seeding ---");
            // Seed if no approved workers exist
            if (workerProfileRepository.countByStatus(WorkerStatus.APPROVED) == 0) {
                seedSkills();
                seedAdmin();
                seedNamedWorkers();
                seedNamedClients();
                seedBulkWorkers();
                seedPendingWorkers();
                seedBulkClients();
            }
            
            // Always refresh interactions for verification
            seedInteractions();
            log.info("--- Database Seeding / Interaction Refresh Complete ---");
        } else {
            log.info("--- Database Seeding Skipped (shouldSeed = false) ---");
        }
    }

    private void seedPendingWorkers() {
        log.info("--- Seeding Pending Workers for Verification ---");
        
        // 1. Fully Complete Pending Worker
        createWorkerStatus("emmanuel_worker", "emmanuel@worker.com", "Emmanuel Otieno", "Plumbing", 25.0, 
            "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?q=80&w=2000&auto=format&fit=crop",
            "Professional plumber with 10 years of experience looking for new opportunities.", WorkerStatus.PENDING, true);

        // 2. Worker with ID verification pending
        createWorkerStatus("jane_electric", "jane@power.com", "Jane Doe", "Electrical Wiring", 35.0,
            "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=2000&auto=format&fit=crop",
            "Licensed electrician specializing in industrial circuits.", WorkerStatus.PENDING, true);

        // 3. Rejected worker (demonstrate re-submission)
        createWorkerStatus("mike_rejected", "mike.r@worker.com", "Mike Rejected", "Masonry", 22.0,
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=2000&auto=format&fit=crop",
            "I was rejected before but I've updated my documents.", WorkerStatus.REJECTED, true);

        // 4. Worker without documents (DRAFT status)
        createWorkerStatus("draft_worker", "draft@worker.com", "Draft User", "General Repairs", 15.0,
            null, "I am just starting out and haven't uploaded docs yet.", WorkerStatus.DRAFT, false);

        // 5. New Pending: Interior Design
        createWorkerStatus("alice_design", "alice@design.com", "Alice Mwangi", "Interior Design", 45.0,
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=2000&auto=format&fit=crop",
            "Creative designer focusing on modern African aesthetics.", WorkerStatus.PENDING, true);
    }

    private void seedInteractions() {
        // Clear existing interactions to ensure fresh seed for verification
        messageRepository.deleteAll();
        notificationRepository.deleteAll();
        reviewRepository.deleteAll();
        jobRequestRepository.deleteAll();

        // Flush deletes
        messageRepository.flush();
        notificationRepository.flush();
        jobRequestRepository.flush();

        log.info("--- Seeding Interlinked Interactions (Jobs & Reviews) ---");

        List<User> clients = new ArrayList<>(userRepository.findAll().stream()
                .filter(u -> UserRole.CLIENT.equals(u.getRole()))
                .sorted((u1, u2) -> u1.getEmail().equals("client@user.com") ? -1 : 1) // Prioritize client@user.com
                .limit(10)
                .collect(Collectors.toList()));
        
        List<WorkerProfile> workers = new ArrayList<>(workerProfileRepository.findAll().stream()
                .filter(w -> w.getStatus() == WorkerStatus.APPROVED && w.getUser() != null)
                .limit(20)
                .collect(Collectors.toList()));

        JobStatus[] statuses = { JobStatus.PENDING, JobStatus.ACCEPTED, JobStatus.COMPLETED, JobStatus.CANCELLED };

        // 1. Seed Client Interactions
        log.info("--- Seeding interaction for client@user.com ---");
        Random random = new Random();
        String[] keywords = {"contract", "payment", "address", "urgent", "thanks", "plumbing", "electrical", "quote", "schedule"};
        String[] attachments = {
            "https://res.cloudinary.com/demo/image/upload/v1/sample.jpg",
            "https://res.cloudinary.com/demo/pdf/upload/v1/sample.pdf",
            "https://res.cloudinary.com/demo/image/upload/v1/couple.jpg"
        };
        
        for (int i = 0; i < Math.min(clients.size(), workers.size()); i++) {
            User client = clients.get(i);
            WorkerProfile worker = workers.get(i);
            JobStatus status = statuses[i % statuses.length];

            // 1. Job Request
            JobRequest job = JobRequest.builder()
                    .client(client)
                    .worker(worker)
                    .description("Service request for " + worker.getCategory())
                    .status(status)
                    .totalCost(worker.getHourlyRate() * (random.nextInt(5) + 1))
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            jobRequestRepository.save(job);

            // 2. Review (only for some completed jobs to leave some for testing)
            if (status == JobStatus.COMPLETED && i % 2 == 0) {
                reviewRepository.save(Review.builder()
                        .client(client)
                        .worker(worker)
                        .rating(5)
                        .comment("Great work!")
                        .build());
            }

            // 3. Message (Deep Conversation)
            if (worker.getUser() != null) {
                List<Message> conversation = new ArrayList<>();
                int messageCount = 2; // Reduced for stable startup
                for (int m = 0; m < messageCount; m++) {
                    boolean isFromClient = m % 2 == 0;
                    User sender = isFromClient ? client : worker.getUser();
                    User receiver = isFromClient ? worker.getUser() : client;
                    String word = keywords[random.nextInt(keywords.length)];
                    String content = (isFromClient ? "Client message: " : "Worker message: ") + "Discussing " + word + " #" + m;
                    
                    Message msg = Message.builder()
                            .sender(sender)
                            .receiver(receiver)
                            .content(content)
                            .isRead(m < messageCount - 2) // Leave last 2 as unread
                            .build();
                    
                    // Add attachment to every 5th message
                    if (m % 5 == 0) {
                        msg.setAttachmentUrl(attachments[random.nextInt(attachments.length)]);
                    }
                    conversation.add(msg);
                }
                messageRepository.saveAll(conversation);
            }

            // 4. Notification
            notificationRepository.save(Notification.builder()
                    .user(worker.getUser())
                    .title("New Hire Request")
                    .message(client.getFullName() + " has requested your services.")
                    .type("INFO")
                    .build());
        }
        
        log.info("Successfully seeded high-volume interactions.");
        
        log.info("Successfully seeded interactions for {} connections.", Math.min(clients.size(), workers.size()));
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
        
        // Seed Admin Notifications
        notificationRepository.save(Notification.builder()
                .user(u)
                .title("Welcome to Kazi Konnect")
                .message("System is ready. 5 workers are pending verification.")
                .type("INFO")
                .build());
        
        notificationRepository.save(Notification.builder()
                .user(u)
                .title("System Audit")
                .message("Scheduled backup completed successfully.")
                .type("SUCCESS")
                .build());

        log.info("Seeded admin: admin@kazikonnect.com / admin123");
    }

    // ─── 3. Named Workers (rich data) ────────────────────────────────────────────
    private void seedNamedWorkers() {
        createWorker("sarah_design", "sarah@design.com", "Sarah Johnson", "Interior Design", 25.0,
                "https://images.unsplash.com/photo-1594744803329-a584af1dd51a?q=80&w=2000&auto=format&fit=crop",
                "Transforming spaces with elegant and functional designs for over 8 years.", WorkerStatus.PENDING);
        createWorker("david_electric", "david@power.com", "David Chen", "Electrical Wiring", 30.0,
                "https://images.unsplash.com/photo-1560250097-0b93528c311a?q=80&w=2000&auto=format&fit=crop",
                "Expert electrician specializing in smart home integration and safety audits.", WorkerStatus.APPROVED);
        createWorker("mike_plumb", "mike@pipes.com", "Michael Smith", "Plumbing", 28.0,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=2000&auto=format&fit=crop",
                "Reliable plumbing solutions for residential and commercial properties.", WorkerStatus.PENDING);
    }

    private void createWorker(String username, String email, String fullName, String category, double rate, String img,
            String bio, WorkerStatus status) {
        createWorkerStatus(username, email, fullName, category, rate, img, bio, status, status == WorkerStatus.APPROVED);
    }

    private void createWorkerStatus(String username, String email, String fullName, String category, double rate, String img, String bio, WorkerStatus status, boolean withDocs) {
        if (userRepository.existsByEmail(email)) return;

        User u = userRepository.save(User.builder()
                .username(username).email(email).fullName(fullName)
                .role(UserRole.WORKER).build());

        authRepository.save(Auth.builder().user(u)
                .passwordHash(passwordEncoder.encode(PASS)).isActive(true).build());

        WorkerProfile profile = workerProfileRepository.save(WorkerProfile.builder()
                .user(u)
                .fullName(fullName)
                .category(category)
                .hourlyRate(rate)
                .bio(bio)
                .profilePictureUrl(img)
                .status(status)
                .isVisible(status == WorkerStatus.APPROVED)
                .isOnline(true)
                .location("Nairobi, Kenya")
                .phoneNumber("07" + String.format("%08d", new Random().nextInt(100000000)))
                .experienceYears(new Random().nextInt(15) + 1)
                .build());

        // Add Skills
        Set<Skill> skills = skillRepository.findAll().stream().limit(3).collect(Collectors.toSet());
        profile.setSkills(skills);
        workerProfileRepository.save(profile);

        // Add Work History
        jdbcTemplate.update("INSERT INTO worker_work_history (id, worker_id, company, role, period, description) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), profile.getId(), "Elite " + category + " Solutions", "Senior " + category, "Jan 2020 - Dec 2023", "Handled high-end projects across the city with exceptional quality.");

        // Add Certification
        jdbcTemplate.update("INSERT INTO worker_certifications (id, worker_id, name, issuer, issue_year) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), profile.getId(), "Certified " + category + " Specialist", "National Industrial Training Authority", 2019);

        if (withDocs) {
            documentRepository.save(Document.builder()
                    .worker(profile)
                    .name("National ID")
                    .type("Identification")
                    .documentUrl("https://res.cloudinary.com/demo/image/upload/v1/sample.jpg")
                    .build());
            
            documentRepository.save(Document.builder()
                    .worker(profile)
                    .name("Trade Certificate")
                    .type("Certification")
                    .documentUrl("https://res.cloudinary.com/demo/image/upload/v1/sample.jpg")
                    .build());
        }

        log.info("Seeded rich {} worker: {}", status, email);
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
        if (workerProfileRepository.count() >= 60)
            return;
        log.info("--- Seeding 60 Professionals ---");
        String[] categories = { "Plumbing", "Electrical Wiring", "Carpentry", "Masonry", "Painting", "Interior Design",
                "General Repairs" };
        for (int i = 1; i <= 60; i++) {
            String email = "worker" + i + "@kazikonnect.com";
            createWorkerStatus("pro_user_" + i, email, "Professional Worker " + i,
                    categories[i % categories.length], 20.0 + (i % 15),
                    "https://images.unsplash.com/photo-1540553016722-983e48a2cd10?q=80&w=2000&auto=format&fit=crop",
                    "Dedicated professional with years of experience in " + categories[i % categories.length] + ".",
                    WorkerStatus.APPROVED, true);
        }
    }

    private void seedBulkClients() {
        if (clientProfileRepository.count() >= 40)
            return;
        log.info("--- Seeding 40 Clients ---");
        for (int i = 1; i <= 40; i++) {
            createClient("client_user_" + i, "client" + i + "@kazikonnect.com", "Client " + i,
                    "Client Profile " + i, "07" + String.format("%08d", 2000 + i));
        }
    }

}
