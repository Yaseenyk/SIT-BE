package org.aisa.api.firestore;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.aisa.api.achievement.Achievement;
import org.aisa.api.achievement.AchievementRepository;
import org.aisa.api.committee.Committee;
import org.aisa.api.committee.CommitteeRepository;
import org.aisa.api.event.Event;
import org.aisa.api.event.EventRepository;
import org.aisa.api.member.Member;
import org.aisa.api.member.MemberRepository;
import org.aisa.api.settings.SiteSettings;
import org.aisa.api.settings.SiteSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the association's structure the first time it runs against an empty project.
 *
 * <p>This is what Flyway's {@code V2__seed_content.sql} used to do. Firestore has no
 * migration concept — no schema to version and no history table — so "has this run?" has to
 * be answered from the data itself. Each block is guarded by a count on its own collection,
 * which means:
 *
 * <ul>
 *   <li>It is idempotent. Restarting the service seeds nothing a second time.
 *   <li>It never overwrites. An admin who edits or deletes seeded content keeps their
 *       version; deleting <em>every</em> committee would let them be reseeded, which is a
 *       reasonable reading of an empty collection and the only way back from a mistake.
 *   <li>It is safe against the existing Firebase project. Pointing this at the original
 *       site's Firestore finds the collections already populated and writes nothing.
 * </ul>
 *
 * <p>Unlike the original page, which seeded from the browser — so whichever visitor loaded
 * it first decided what everyone else saw — this runs once, on the server, before the first
 * request is served.
 */
@Configuration
public class FirestoreSeeder {

    private static final Logger log = LoggerFactory.getLogger(FirestoreSeeder.class);

    @Bean
    ApplicationRunner seedFirestore(
            CommitteeRepository committees,
            MemberRepository members,
            EventRepository events,
            AchievementRepository achievements,
            SiteSettingsRepository settings,
            Clock clock) {
        return args -> {
            if (committees.count() == 0) {
                seedCommittees(committees);
                log.info("Seeded 10 committees.");
            }
            if (members.count() == 0) {
                seedMembers(members);
                log.info("Seeded office-bearers.");
            }
            if (events.count() == 0) {
                seedEvents(events, LocalDate.now(clock));
                log.info("Seeded events.");
            }
            if (achievements.count() == 0) {
                seedAchievements(achievements, LocalDate.now(clock));
                log.info("Seeded achievements.");
            }
            if (settings.find().isEmpty()) {
                settings.save(defaultSettings());
                log.info("Seeded site settings.");
            }
        };
    }

    private static Committee committee(
            String id, int order, String type, String name, String icon, String gradient,
            String size, String badge, List<String> responsibilities) {
        Committee c = new Committee(id);
        c.setDisplayOrder(order);
        c.setType(type);
        c.setName(name);
        c.setIcon(icon);
        c.setGradient(gradient);
        c.setSizeLabel(size);
        c.setBadge(badge);
        c.setResponsibilities(responsibilities);
        return c;
    }

    private static void seedCommittees(CommitteeRepository repo) {
        Committee advisory = committee("advisory", 0, "advisory", "Faculty Advisory Committee", "🎓",
                "linear-gradient(135deg,#1e40af,#3b82f6)", "2 members", "Faculty Advisory",
                List.of("Provide academic and administrative guidance to the association",
                        "Approve events, workshops, seminars, and competitions",
                        "Ensure activities align with department and university policies",
                        "Support collaboration with industry experts, researchers, and alumni",
                        "Monitor budget usage and documentation",
                        "Evaluate the performance and impact of association activities"));
        advisory.setCoordLabel("Department Head");
        advisory.setCoordinator("Prof. A.A. Paritekar");
        advisory.setCoordinatorSub("CSE-AIML");
        advisory.setCoord2Label("AISA Coordinator");
        advisory.setCoordinator2("Prof. S.S. Rabade");
        repo.save(advisory);

        repo.save(committee("president", 1, "executive", "President", "👑",
                "linear-gradient(135deg,#78350f,#d97706)", "1 student", "Executive",
                List.of("Provide overall leadership and vision for the association",
                        "Conduct regular meetings with committee members",
                        "Coordinate with faculty advisors and department administration",
                        "Represent the association in departmental meetings",
                        "Approve project proposals, events, and initiatives",
                        "Ensure smooth execution of all activities")));

        repo.save(committee("vp", 2, "executive", "Vice President", "🤝",
                "linear-gradient(135deg,#065f46,#059669)", "1 student", "Executive",
                List.of("Assist the President in planning and executing activities",
                        "Coordinate between different committees",
                        "Supervise event planning and execution",
                        "Take charge of the association in the absence of the President",
                        "Monitor the progress of ongoing projects and events")));

        repo.save(committee("treasurer", 3, "executive", "Treasurer", "💰",
                "linear-gradient(135deg,#7c2d12,#c2410c)", "1 student", "Executive",
                List.of("Prepare budget plans for events and activities",
                        "Maintain records of income and expenditures",
                        "Handle fund collection, sponsorship funds, and reimbursements",
                        "Submit financial reports to faculty advisors",
                        "Ensure transparency and proper use of funds")));

        repo.save(committee("secretary", 4, "executive", "General Secretary", "📋",
                "linear-gradient(135deg,#1e3a8a,#1d4ed8)", "1 student", "Executive",
                List.of("Maintain records, meeting minutes, and official documents",
                        "Prepare activity reports and event documentation",
                        "Manage official communication with members and faculty",
                        "Coordinate scheduling of meetings and activities",
                        "Maintain the association membership database")));

        repo.save(committee("sports", 5, "functional", "Sports Committee", "🏅",
                "linear-gradient(135deg,#134e4a,#0f766e)", "2-3 students", "Functional",
                List.of("Organise inter-class and inter-department sports events",
                        "Coordinate participation in university-level sports competitions",
                        "Promote physical wellness and team-building among AIML students",
                        "Manage sports equipment and booking of facilities")));

        repo.save(committee("events", 6, "functional", "Event Management Committee", "🎪",
                "linear-gradient(135deg,#7f1d1d,#b91c1c)", "4-5 students", "Functional",
                List.of("Plan technical fests, workshops, competitions, and seminars",
                        "Prepare event schedules and logistics",
                        "Coordinate with technical, publicity, and finance teams",
                        "Manage venue arrangements, registrations, and certificates",
                        "Ensure smooth event execution")));

        Committee research = committee("research", 7, "functional", "Research & Innovation Committee", "🔬",
                "linear-gradient(135deg,#4a044e,#7e22ce)", "3-4 students", "Functional",
                List.of("Encourage research paper writing and publications",
                        "Organize paper presentation competitions",
                        "Help students identify research problems in AIML",
                        "Conduct sessions on research methodology and tools",
                        "Support patent filing and innovation projects",
                        "Collaborate with research labs and industry"));
        research.setCoordLabel("Faculty Mentor");
        research.setCoordinator("Prof. A.A. Paritekar");
        repo.save(research);

        Committee technical = committee("technical", 8, "functional", "Technical Committee", "⚙️",
                "linear-gradient(135deg,#0c4a6e,#0369a1)", "4-6 students", "Functional",
                List.of("Organize AIML workshops, coding sessions, and hackathons",
                        "Conduct seminars on deep learning, computer vision, NLP, etc.",
                        "Arrange guest lectures from industry experts and researchers",
                        "Promote student research projects and innovation",
                        "Help students participate in AI competitions and conferences",
                        "Maintain GitHub repositories and technical resources"));
        technical.setCoordLabel("Faculty Mentor");
        technical.setCoordinator("Prof. S.S. Rabade");
        repo.save(technical);

        repo.save(committee("media", 9, "functional", "Media, Design & Publicity", "📸",
                "linear-gradient(135deg,#3b0764,#6d28d9)", "3-4 students", "Functional",
                List.of("Design posters, banners, and promotional materials",
                        "Manage social media accounts and association website",
                        "Publicize upcoming events and achievements",
                        "Document events through photos and videos",
                        "Create newsletters and activity reports")));
    }

    /** Sample names. Fictional, and meant to be replaced from the admin panel. */
    private static void seedMembers(MemberRepository repo) {
        record Seed(String name, String role, String committeeId, String year, int order) {}
        List<Seed> seeds = List.of(
                new Seed("Aditi Kulkarni", "President", "president", "3rd Year", 0),
                new Seed("Rohan Deshmukh", "Vice President", "vp", "3rd Year", 1),
                new Seed("Sanika Patil", "Treasurer", "treasurer", "2nd Year", 2),
                new Seed("Omkar Jadhav", "General Secretary", "secretary", "2nd Year", 3),
                new Seed("Prathamesh Shinde", "Sports Coordinator", "sports", "2nd Year", 4),
                new Seed("Sneha Bhosale", "Events Head", "events", "3rd Year", 5),
                new Seed("Aryan Chavan", "Research Lead", "research", "3rd Year", 6),
                new Seed("Isha Sawant", "Technical Head", "technical", "3rd Year", 7),
                new Seed("Kaustubh Mane", "Media Head", "media", "2nd Year", 8),
                new Seed("Tanvi Gaikwad", "Technical Member", "technical", "2nd Year", 9),
                new Seed("Sarthak Pawar", "Technical Member", "technical", "3rd Year", 10),
                new Seed("Neha Salunkhe", "Research Member", "research", "3rd Year", 11),
                new Seed("Vedant Kadam", "Events Member", "events", "2nd Year", 12),
                new Seed("Riya Naik", "Design Lead", "media", "2nd Year", 13),
                new Seed("Atharva Joshi", "Sports Member", "sports", "1st Year", 14));

        for (Seed seed : seeds) {
            Member member = new Member(seed.name(), seed.role());
            member.setCommitteeId(seed.committeeId());
            member.setAcademicYear(seed.year());
            member.setDisplayOrder(seed.order());
            repo.save(member);
        }
    }

    /**
     * Dated relative to today, so the Upcoming tab is never empty on a fresh install
     * however long after this was written the project is first created.
     */
    private static void seedEvents(EventRepository repo, LocalDate today) {
        record Seed(String title, int startOffset, Integer endOffset, String tag, String emoji, String description) {}
        List<Seed> seeds = List.of(
                new Seed("Neural Networks from Scratch", 12, null, "Workshop", "🧠",
                        "Build a working neural network in pure NumPy before touching a framework. "
                                + "Backpropagation derived on the board, then written line by line. Laptops required."),
                new Seed("AISA Hackathon 4.0", 26, 27, "Hackathon", "⚡",
                        "36 hours, four tracks, one working prototype. Open to all years - teams of up to four. "
                                + "Mentors from industry on the floor through the night."),
                new Seed("Guest Lecture: LLMs in Production", 40, null, "Lecture", "🎤",
                        "What actually breaks when a language model meets real users - latency, cost, "
                                + "evaluation and the parts nobody writes papers about."),
                new Seed("Computer Vision Bootcamp", 54, 56, "Bootcamp", "👁️",
                        "Three days from convolutions to a deployed detector. OpenCV, PyTorch, and a final "
                                + "project you take home."),
                new Seed("Research Paper Writing Clinic", 68, null, "Research", "📄",
                        "Structure, related work, and how to survive peer review. Bring a draft or an idea; "
                                + "leave with an outline and a target venue."),
                new Seed("Inter-College AI Quiz 2.0", 82, null, "Competition", "🏆",
                        "Sixteen colleges, four rounds, one trophy. Registration opens a month before."),
                new Seed("Introduction to Generative AI", -30, null, "Lecture", "🎤",
                        "Guest lecture on LLMs, diffusion models and the future of generative AI."),
                new Seed("Python for AI - Bootcamp", -95, -93, "Bootcamp", "🐍",
                        "3-day bootcamp on Python, NumPy, Pandas, and scikit-learn for students new to AIML."),
                new Seed("Inter-College AI Quiz", -120, null, "Competition", "🏆",
                        "AISA hosted an AIML quiz with 12 colleges across the region participating."),
                new Seed("OpenCV Workshop", -60, null, "Workshop", "👁️",
                        "Computer vision fundamentals with OpenCV and Python by the Technical Committee."));

        for (Seed seed : seeds) {
            Event event = new Event(seed.title(), today.plusDays(seed.startOffset()));
            if (seed.endOffset() != null) {
                event.setEndsOn(today.plusDays(seed.endOffset()));
            }
            event.setTag(seed.tag());
            event.setEmoji(seed.emoji());
            event.setDescription(seed.description());
            repo.save(event);
        }
    }

    private static void seedAchievements(AchievementRepository repo, LocalDate today) {
        record Seed(String title, String student, String category, int daysAgo, String description) {}
        List<Seed> seeds = List.of(
                new Seed("Winner - Smart India Hackathon (Regional)", "Isha Sawant", "competition", 45,
                        "Led a four-member team to first place with a crop-disease detector running entirely on-device."),
                new Seed("Paper accepted at ICACCI 2026", "Aryan Chavan", "research", 72,
                        "Co-authored \"Lightweight Transformers for Regional Language OCR\", accepted in the student track."),
                new Seed("Machine Learning Intern, Persistent Systems", "Rohan Deshmukh", "internship", 110,
                        "Six-month internship on the recommendation platform team, converted to a pre-placement offer."),
                new Seed("Runner-up - Kavach National Cybersecurity Hackathon", "Sarthak Pawar", "competition", 150,
                        "Second nationally for an anomaly-detection pipeline built over 48 hours."),
                new Seed("Best Paper - State Level Technical Symposium", "Neha Salunkhe", "research", 190,
                        "Recognised for work on federated learning across low-bandwidth rural clinics."),
                new Seed("Data Science Intern, Tata Consultancy Services", "Tanvi Gaikwad", "internship", 220,
                        "Built forecasting models for retail demand planning during a summer internship."));

        for (Seed seed : seeds) {
            Achievement achievement = new Achievement(seed.title(), seed.student());
            achievement.setCategory(seed.category());
            achievement.setAchievedOn(today.minusDays(seed.daysAgo()));
            achievement.setDescription(seed.description());
            repo.save(achievement);
        }
    }

    private static SiteSettings defaultSettings() {
        SiteSettings s = new SiteSettings();
        s.setPhone("0231 265 8613");
        s.setEmail("aiml@bsiet.edu.in");
        s.setAddress("Dr. Bapuji Salunkhe Institute of Engineering & Technology, Kolhapur, Maharashtra");
        s.setWebsite("https://bsiet.edu.in");
        s.setAboutTitle("About AISA");
        s.setAboutDescription(
                "AISA is the student body of the Department of Computer Science & Engineering (AI & ML) "
                        + "at Dr. Bapuji Salunkhe Institute of Engineering & Technology, Kolhapur. We run the "
                        + "workshops, hackathons and reading groups that turn a syllabus into something you can "
                        + "build with - and we make sure every student who wants to get their hands on this work "
                        + "has somewhere to start.");
        s.setFeature1Title("Learn");
        s.setFeature1Description("Workshops, bootcamps and hands-on sessions across the AIML stack.");
        s.setFeature2Title("Build");
        s.setFeature2Description("Hackathons and project teams that turn coursework into something that runs.");
        s.setFeature3Title("Research");
        s.setFeature3Description("Paper writing, presentation practice, and guidance towards publication.");
        s.setFeature4Title("Connect");
        s.setFeature4Description("Guest lectures, alumni talks, and industry collaboration.");
        return s;
    }
}
