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
                
                // Only refresh interactions during full seed
                seedInteractions();
            }
            
            // Always run name cleanup to ensure "Unknown" is replaced by real names
            fixExistingUserNames();
            
            log.info("--- Database Seeding Complete ---");
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

        log.info("--- Seeding Comprehensive Marketplace Interactions ---");

        User mainClient = userRepository.findByEmail("client@user.com").orElse(null);
        WorkerProfile mainWorker = workerProfileRepository.findAll().stream()
                .filter(w -> w.getUser() != null && w.getUser().getEmail().equals("worker4@kazikonnect.com"))
                .findFirst().orElse(null);

        if (mainClient == null || mainWorker == null) {
            log.error("Main test accounts not found. Skipping interaction seed.");
            return;
        }

        Random random = new Random();
        String[] taskDescriptions = {
            "Fix leaking pipe in the master bathroom.",
            "Install 5 new LED ceiling lights in the living room.",
            "Repair broken kitchen cabinet hinges.",
            "Repaint the guest bedroom walls (Light Gray).",
            "Emergency repair of a blown fuse box.",
            "Install new hardwood flooring in the study room.",
            "Design a modern open-plan kitchen layout.",
            "Mount 65-inch TV on a concrete wall.",
            "General maintenance of garden irrigation system."
        };

        JobStatus[] statuses = { 
            JobStatus.PENDING, 
            JobStatus.ACCEPTED, 
            JobStatus.IN_PROGRESS, 
            JobStatus.SUBMITTED, 
            JobStatus.REVISION_REQUESTED, 
            JobStatus.APPROVED, 
            JobStatus.COMPLETED, 
            JobStatus.DISPUTED, 
            JobStatus.CANCELLED 
        };

        for (int i = 0; i < statuses.length; i++) {
            JobStatus status = statuses[i];
            
            // 1. Create Job Request
            JobRequest job = JobRequest.builder()
                    .client(mainClient)
                    .worker(mainWorker)
                    .description(taskDescriptions[i % taskDescriptions.length])
                    .status(status)
                    .totalCost(mainWorker.getHourlyRate() * (random.nextInt(10) + 2))
                    .createdAt(java.time.LocalDateTime.now().minusDays(statuses.length - i))
                    .build();
            jobRequestRepository.save(job);

            // 2. Add Review for Completed Job
            if (status == JobStatus.COMPLETED) {
                reviewRepository.save(Review.builder()
                        .client(mainClient)
                        .worker(mainWorker)
                        .jobRequest(job)
                        .rating(5)
                        .comment("Exceptional quality and professionalism. Highly recommended!")
                        .build());
            }

            // 3. Add Messages
            if (mainWorker.getUser() != null) {
                String[] clientMessages = {
                    "Hi, when can you start on the " + job.getDescription() + "?",
                    "Is the project still on schedule?",
                    "I noticed a small issue with the alignment.",
                    "The work looks great so far!"
                };
                String[] workerMessages = {
                    "I can start tomorrow morning at 9 AM.",
                    "Yes, I should be done by Wednesday.",
                    "No problem, I will fix that immediately.",
                    "Thank you! I've just submitted the work for your approval."
                };

                for (int m = 0; m < 2; m++) {
                    messageRepository.save(Message.builder()
                            .sender(mainClient)
                            .receiver(mainWorker.getUser())
                            .content(clientMessages[random.nextInt(clientMessages.length)])
                            .isRead(true)
                            .build());
                    
                    messageRepository.save(Message.builder()
                            .sender(mainWorker.getUser())
                            .receiver(mainClient)
                            .content(workerMessages[random.nextInt(workerMessages.length)])
                            .isRead(i % 2 == 0) // Mix of read/unread
                            .build());
                }
            }

            // 4. Add Notifications based on status
            if (status == JobStatus.SUBMITTED) {
                notificationRepository.save(Notification.builder()
                        .user(mainClient)
                        .title("Work Delivered!")
                        .message(mainWorker.getFullName() + " has submitted work for approval.")
                        .type("SUCCESS")
                        .build());
            } else if (status == JobStatus.DISPUTED) {
                notificationRepository.save(Notification.builder()
                        .user(mainClient)
                        .title("Dispute Opened")
                        .message("Your dispute for job #" + job.getId().toString().substring(0, 8) + " is under review.")
                        .type("WARNING")
                        .build());
            } else if (status == JobStatus.REVISION_REQUESTED) {
                notificationRepository.save(Notification.builder()
                        .user(mainWorker.getUser())
                        .title("Revision Requested")
                        .message(mainClient.getFullName() + " has requested changes to the work.")
                        .type("INFO")
                        .build());
            }
        }

        log.info("Successfully seeded comprehensive interactions for test accounts.");
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
                .firstName("System").secondName("Administrator")
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

        String[] parts = fullName.split(" ", 2);
        String fName = parts[0];
        String sName = parts.length > 1 ? parts[1] : "";

        User u = userRepository.save(User.builder()
                .username(username).email(email)
                .firstName(fName).secondName(sName).fullName(fullName)
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
        String[] parts = fullName.split(" ", 2);
        String fName = parts[0];
        String sName = parts.length > 1 ? parts[1] : "";

        User u = userRepository.save(User.builder()
                .username(username).email(email)
                .firstName(fName).secondName(sName).fullName(fullName)
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

    private void fixExistingUserNames() {
        log.info("--- Cleaning up User names (Optimized Bulk Update) ---");
        try {
            int both = userRepository.updateFullNamesFromFirstAndSecond();
            int first = userRepository.updateFullNamesFromFirstOnly();
            int fallback = userRepository.updateFullNamesFromUsername();
            log.info("Name cleanup results: {} fixed with full name, {} fixed with first name, {} fixed with username fallback.", both, first, fallback);
        } catch (Exception e) {
            log.error("Failed to run bulk name cleanup: {}", e.getMessage());
        }
    }
}
