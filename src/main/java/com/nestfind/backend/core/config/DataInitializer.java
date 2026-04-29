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
    private final WorkerProfileRepository workerProfileRepository;
    private final WorkHistoryRepository workHistoryRepository;
    private final CertificationRepository certificationRepository;
    private final DocumentRepository documentRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String PASS = "password123";

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.existsByEmail("admin@nestfind.com")) {
            log.info("Database already seeded. Skipping initialization.");
            return;
        }

        log.info("--- Starting Database Seeding ---");
        // cleanup(); // Disabled for faster startup and data persistence
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
        createWorker("kevin_pro", "kevin@worker.com", "Kevin Ochieng",
            "0711223344", "Nairobi", "Certified plumber with 6 years in residential and commercial maintenance.",
            "Plumbing", 6, 1500.0, WorkerStatus.APPROVED, true,
            "https://randomuser.me/api/portraits/men/32.jpg",
            new String[]{"Plumbing", "General Repairs"},
            new String[]{"Nairobi", "Thika", "Kiambu"},
            new String[][]{
                {"Senior Plumber", "Kenya Plumbing Co.", "2018–2022", "Led pipe installation for 200+ homes."},
                {"Apprentice Plumber", "BuildRight Ltd.", "2016–2018", "Assisted in commercial building projects."}
            },
            new String[][]{
                {"Certified Plumbing Technician", "Kenya National Qualifications Authority", "2018"},
                {"Water Safety & Sanitation", "Nairobi City Council", "2020"}
            });

        createWorker("grace_elect", "grace@worker.com", "Grace Njeri",
            "0722334455", "Mombasa", "Licensed electrician specializing in solar installations and wiring.",
            "Electrical Wiring", 5, 2000.0, WorkerStatus.APPROVED, true,
            "https://randomuser.me/api/portraits/women/44.jpg",
            new String[]{"Electrical Wiring", "HVAC Installation"},
            new String[]{"Mombasa", "Kilifi", "Kwale"},
            new String[][]{
                {"Lead Electrician", "Coastal Power Solutions", "2019–2024", "Managed solar panel installations for 500+ clients."},
                {"Junior Electrician", "Sun Energy Kenya", "2017–2019", "Wired residential complexes and offices."}
            },
            new String[][]{
                {"Electrical Installation Certificate", "Kenya Power", "2019"},
                {"Solar Systems Technician", "Africa Solar Institute", "2021"}
            });

        createWorker("michael_carp", "michael@worker.com", "Michael Mutua",
            "0733445566", "Kisumu", "Expert carpenter building custom furniture and structural woodwork.",
            "Carpentry", 8, 1800.0, WorkerStatus.PENDING, false,
            "https://randomuser.me/api/portraits/men/55.jpg",
            new String[]{"Carpentry", "Interior Design"},
            new String[]{"Kisumu", "Kakamega", "Siaya"},
            new String[][]{
                {"Master Carpenter", "Lakeside Furniture Works", "2016–2024", "Crafted bespoke furniture for hotels and offices."},
                {"Cabinet Maker", "Homecraft Ltd.", "2014–2016", "Produced custom kitchen cabinets."}
            },
            new String[][]{
                {"Advanced Woodworking", "Kenya Polytechnic", "2016"},
                {"Interior Finishing Specialist", "NITA Kenya", "2019"}
            });

        createWorker("sarah_paint", "sarah@worker.com", "Sarah Anyango",
            "0744556677", "Nakuru", "Creative painter delivering premium interior and exterior finishes.",
            "Painting", 4, 1200.0, WorkerStatus.PENDING, false,
            "https://randomuser.me/api/portraits/women/61.jpg",
            new String[]{"Painting", "Interior Design"},
            new String[]{"Nakuru", "Eldoret", "Naivasha"},
            new String[][]{
                {"Senior Painter", "Nakuru Décor Studio", "2020–2024", "Delivered high-end residential painting projects."},
                {"Painter", "ColorPro Kenya", "2018–2020", "Interior and exterior painting for apartments."}
            },
            new String[][]{
                {"Decorative Painting Certificate", "Kenya Institute of Painters", "2020"}
            });

        createWorker("david_hvac", "david@worker.com", "David Kipkorir",
            "0755667788", "Eldoret", "HVAC specialist with expertise in commercial air conditioning systems.",
            "HVAC Installation", 7, 2500.0, WorkerStatus.REJECTED, false,
            "https://randomuser.me/api/portraits/men/70.jpg",
            new String[]{"HVAC Installation", "Electrical Wiring"},
            new String[]{"Eldoret", "Nakuru", "Kitale"},
            new String[][]{
                {"HVAC Engineer", "CoolTech Systems", "2017–2024", "Installed and maintained AC units for hospitals."},
                {"HVAC Technician", "Frosty Air Ltd.", "2015–2017", "Serviced residential cooling systems."}
            },
            new String[][]{
                {"HVAC Refrigeration Certificate", "EPRA Kenya", "2017"},
                {"Building Services Engineering", "EBK Kenya", "2019"}
            });

        createWorker("linda_mason", "linda@worker.com", "Linda Kamau",
            "0766778899", "Thika", "Skilled mason with expertise in foundations, walls, and tiling.",
            "Masonry", 9, 1600.0, WorkerStatus.APPROVED, true,
            "https://randomuser.me/api/portraits/women/33.jpg",
            new String[]{"Masonry", "Tiling"},
            new String[]{"Thika", "Nairobi", "Murang'a"},
            new String[][]{
                {"Site Foreman", "Thika Construction Co.", "2015–2024", "Supervised masonry on 30+ construction projects."},
                {"Block Layer", "Rapid Build Kenya", "2013–2015", "Laid foundations and walls for residential homes."}
            },
            new String[][]{
                {"Construction Masonry", "NITA Kenya", "2015"},
                {"Structural Tiles Expert", "Kenya Building Institute", "2018"}
            });
    }

    private void createWorker(
            String username, String email, String fullName, String phone,
            String location, String bio, String category, int exp, double rate,
            WorkerStatus status, boolean visible, String picUrl,
            String[] skillNames, String[] prefLocations,
            String[][] history, String[][] certs) {

        if (userRepository.existsByEmail(email)) return;

        User u = userRepository.save(User.builder()
            .username(username).email(email).fullName(fullName)
            .role(UserRole.WORKER).build());
        authRepository.save(Auth.builder().user(u)
            .passwordHash(passwordEncoder.encode(PASS)).isActive(true).build());

        Set<Skill> skills = new HashSet<>();
        for (String sn : skillNames) {
            skillRepository.findByName(sn).ifPresent(skills::add);
        }

        WorkerProfile p = workerProfileRepository.save(WorkerProfile.builder()
            .user(u).fullName(fullName).phoneNumber(phone)
            .location(location).bio(bio).category(category)
            .experienceYears(exp).hourlyRate(rate).status(status)
            .isVisible(visible).isOnline(visible)
            .profilePictureUrl(picUrl).skills(skills)
            .preferredLocations(new HashSet<>(Arrays.asList(prefLocations)))
            .rejectionReason(status == WorkerStatus.REJECTED
                ? "Incomplete document submission. Please resubmit with valid national ID." : null)
            .build());

        for (String[] h : history) {
            workHistoryRepository.save(WorkHistory.builder()
                .worker(p).role(h[0]).company(h[1]).period(h[2]).description(h[3]).build());
        }
        for (String[] c : certs) {
            certificationRepository.save(Certification.builder()
                .worker(p).name(c[0]).issuer(c[1]).year(Integer.parseInt(c[2])).build());
        }
        
        // Add default documents
        documentRepository.save(Document.builder()
            .worker(p)
            .name("National_ID")
            .type("ID_CARD")
            .documentUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg")
            .build());
            
        documentRepository.save(Document.builder()
            .worker(p)
            .name("Technical_Certificate")
            .type("CERTIFICATE")
            .documentUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg")
            .build());

        log.info("Seeded worker: {} ({})", email, status);
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

    // ─── 5. Bulk Workers ─────────────────────────────────────────────────────────
    private void seedBulkWorkers() {
        if (workerProfileRepository.count() >= 30) return;
        log.info("--- Seeding bulk workers ---");

        String[] firstNames = {"Samuel", "James", "Sarah", "Kevin", "Emily",
                               "Michael", "Linda", "David", "Grace", "Robert",
                               "John", "Mary", "Peter", "Alice", "George"};
        String[] lastNames  = {"Ochieng", "Mutua", "Wanjiru", "Omondi", "Anyango",
                               "Kipkorir", "Njeri", "Kamau", "Otieno", "Maina",
                               "Kariuki", "Akinyi", "Musyoka", "Wairimu", "Ndiema"};
        String[] categories = {"Plumbing", "Electrical Wiring", "Carpentry",
                               "Masonry", "Painting", "HVAC Installation",
                               "Roofing", "Tiling", "Welding", "General Repairs"};
        String[] locations  = {"Nairobi", "Mombasa", "Kisumu", "Nakuru",
                               "Eldoret", "Thika", "Machakos", "Nyeri"};
        String[] picUrls    = {
            "https://randomuser.me/api/portraits/men/1.jpg",
            "https://randomuser.me/api/portraits/women/2.jpg",
            "https://randomuser.me/api/portraits/men/3.jpg",
            "https://randomuser.me/api/portraits/women/4.jpg",
            "https://randomuser.me/api/portraits/men/5.jpg",
            "https://randomuser.me/api/portraits/women/6.jpg",
            "https://randomuser.me/api/portraits/men/7.jpg",
            "https://randomuser.me/api/portraits/women/8.jpg",
        };
        WorkerStatus[] statuses = {WorkerStatus.APPROVED, WorkerStatus.APPROVED,
                                   WorkerStatus.APPROVED, WorkerStatus.PENDING,
                                   WorkerStatus.REJECTED};

        for (int i = 0; i < 50; i++) {
            String email = "worker" + i + "@bulk.com";
            if (userRepository.existsByEmail(email)) continue;

            String fn = firstNames[i % firstNames.length];
            String ln = lastNames[(i / firstNames.length) % lastNames.length];
            String cat = categories[i % categories.length];
            String loc = locations[i % locations.length];
            WorkerStatus st = statuses[i % statuses.length];
            boolean visible = st == WorkerStatus.APPROVED;
            int exp = (i % 12) + 1;
            double rate = 800 + (i % 20) * 100.0;
            String pic = picUrls[i % picUrls.length];

            User u = userRepository.save(User.builder()
                .username("worker_bulk_" + i).email(email)
                .fullName(fn + " " + ln).role(UserRole.WORKER).build());
            authRepository.save(Auth.builder().user(u)
                .passwordHash(passwordEncoder.encode(PASS)).isActive(true).build());

            Set<Skill> skills = new HashSet<>();
            skillRepository.findByName(cat).ifPresent(skills::add);
            skillRepository.findByName("General Repairs").ifPresent(skills::add);

            WorkerProfile p = workerProfileRepository.save(WorkerProfile.builder()
                .user(u).fullName(fn + " " + ln)
                .phoneNumber("07" + String.format("%08d", i))
                .location(loc).bio("Professional " + cat + " specialist with " + exp + " years of experience in " + loc + ".")
                .category(cat).experienceYears(exp).hourlyRate(rate)
                .status(st).isVisible(visible).isOnline(i % 3 == 0)
                .profilePictureUrl(pic)
                .skills(skills)
                .preferredLocations(new HashSet<>(Set.of(loc, locations[(i + 1) % locations.length])))
                .rejectionReason(st == WorkerStatus.REJECTED
                    ? "Documentation incomplete. Please resubmit with certified copies." : null)
                .build());

            workHistoryRepository.save(WorkHistory.builder()
                .worker(p).role("Senior " + cat + " Technician")
                .company(loc + " Construction Ltd.")
                .period("2019–2023")
                .description("Delivered " + cat.toLowerCase() + " services for over 100 residential and commercial clients in " + loc + ".")
                .build());

            workHistoryRepository.save(WorkHistory.builder()
                .worker(p).role("Junior " + cat + " Assistant")
                .company("BuildPro Kenya")
                .period("2016–2019")
                .description("Assisted senior technicians in project execution and site management.")
                .build());

            certificationRepository.save(Certification.builder()
                .worker(p).name(cat + " Technician Certificate")
                .issuer("NITA Kenya").year(2019).build());

            certificationRepository.save(Certification.builder()
                .worker(p).name("Occupational Health & Safety")
                .issuer("DOSH Kenya").year(2021).build());
                
            // Bulk documents
            documentRepository.save(Document.builder()
                .worker(p)
                .name("National_ID")
                .type("ID_CARD")
                .documentUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg")
                .build());
        }
        log.info("Seeded bulk workers.");
    }

    // ─── 6. Bulk Clients ─────────────────────────────────────────────────────────
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
