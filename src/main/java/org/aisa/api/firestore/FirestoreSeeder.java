package org.aisa.api.firestore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.core.io.ClassPathResource;

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
 *       version; emptying a collection entirely lets it be reseeded, which is a reasonable
 *       reading of an empty collection and the only way back from a mistake.
 * </ul>
 *
 * <p>Unlike the original page, which seeded from the browser — so whichever visitor loaded
 * it first decided what everyone else saw — this runs once, on the server, before the first
 * request is served.
 *
 * <h2>Where the content comes from</h2>
 *
 * The committees, members and site settings live in {@code resources/seed/aisa-seed.json},
 * not in this file. That file was generated from the association's own Firestore, so the
 * committee structure, the responsibilities, the faculty coordinators, the real
 * office-bearers and the real contact details are theirs rather than invented — with the
 * legacy field names normalised and the duplicate roster entries collapsed.
 *
 * <p>Keeping it as a resource rather than Java literals means an admin can read and edit
 * it, and that adding a committee does not mean recompiling. Events and achievements stay
 * in code because they are dated relative to "today".
 */
@Configuration
public class FirestoreSeeder {

    private static final Logger log = LoggerFactory.getLogger(FirestoreSeeder.class);
    private static final String SEED_FILE = "seed/aisa-seed.json";

    @Bean
    ApplicationRunner seedFirestore(
            CommitteeRepository committees,
            MemberRepository members,
            EventRepository events,
            AchievementRepository achievements,
            SiteSettingsRepository settings,
            Clock clock) {
        return args -> {
            /*
             * The first Firestore call the application ever makes lands here, so this is
             * where a misconfiguration surfaces. Left alone it surfaces as
             * "Firestore call failed while reading committees" plus a gRPC stack trace,
             * which names none of the three things that are ever actually wrong — and on a
             * host like Render it presents as a crash loop rather than a message.
             *
             * Failing fast is still right: a backend that cannot reach its database is not
             * healthy and should not accept traffic. It just has to say why.
             */
            try {
                committees.count();
            } catch (RuntimeException ex) {
                throw new IllegalStateException("""
                        Cannot reach Firestore. The usual causes, in order of likelihood:
                          1. The database has not been created yet — Firebase Console ->
                             Build -> Firestore Database -> Create database (Native mode).
                          2. FIREBASE_SERVICE_ACCOUNT is missing, truncated, or is the
                             base64 of a different project's key.
                          3. FIREBASE_PROJECT_ID names a project the key has no access to.
                        See the cause below for what the client actually reported.""", ex);
            }

            JsonNode seed = readSeedFile();

            if (committees.count() == 0) {
                int n = seedCommittees(committees, seed.get("committees"));
                log.info("Seeded {} committees.", n);
            }
            if (members.count() == 0) {
                int n = seedMembers(members, seed.get("members"));
                log.info("Seeded {} office-bearers.", n);
            }
            if (events.count() == 0) {
                int n = seedEvents(events, LocalDate.now(clock));
                log.info("Seeded {} events.", n);
            }
            if (achievements.count() == 0) {
                int n = seedAchievements(achievements, LocalDate.now(clock));
                log.info("Seeded {} achievements.", n);
            }
            if (settings.find().isEmpty()) {
                settings.save(toSettings(seed.get("settings")));
                log.info("Seeded site settings.");
            }
        };
    }

    private static JsonNode readSeedFile() {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return new ObjectMapper().readTree(in);
        } catch (IOException ex) {
            // Packaged with the jar, so this only fails if the build is broken. Failing
            // loudly beats starting with an empty site and no explanation.
            throw new IllegalStateException("Could not read " + SEED_FILE + " from the classpath", ex);
        }
    }

    /** Null for an absent or JSON-null field, so blanks never become the string "null". */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = value.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static int seedCommittees(CommitteeRepository repo, JsonNode array) {
        for (JsonNode node : array) {
            Committee c = new Committee(node.get("id").asText());
            c.setDisplayOrder(node.get("displayOrder").asInt());
            c.setType(text(node, "type"));
            c.setName(text(node, "name"));
            c.setIcon(text(node, "icon"));
            c.setGradient(text(node, "gradient"));
            c.setSizeLabel(text(node, "sizeLabel"));
            c.setBadge(text(node, "badge"));
            c.setCoordLabel(text(node, "coordLabel"));
            c.setCoordinator(text(node, "coordinator"));
            c.setCoordinatorSub(text(node, "coordinatorSub"));
            c.setCoord2Label(text(node, "coord2Label"));
            c.setCoordinator2(text(node, "coordinator2"));

            List<String> responsibilities = new ArrayList<>();
            for (JsonNode r : node.withArray("responsibilities")) {
                responsibilities.add(r.asText());
            }
            c.setResponsibilities(responsibilities);
            repo.save(c);
        }
        return array.size();
    }

    private static int seedMembers(MemberRepository repo, JsonNode array) {
        for (JsonNode node : array) {
            Member m = new Member(text(node, "name"), text(node, "role"));
            m.setCommitteeId(text(node, "committeeId"));
            m.setAcademicYear(text(node, "academicYear"));
            m.setLinkedinUrl(text(node, "linkedinUrl"));
            m.setGithubUrl(text(node, "githubUrl"));
            m.setEmail(text(node, "email"));
            m.setPhotoUrl(text(node, "photoUrl"));
            m.setDisplayOrder(node.get("displayOrder").asInt());
            repo.save(m);
        }
        return array.size();
    }

    private static SiteSettings toSettings(JsonNode node) {
        SiteSettings s = new SiteSettings();
        s.setPhone(text(node, "phone"));
        s.setEmail(text(node, "email"));
        s.setAddress(text(node, "address"));
        s.setWebsite(text(node, "website"));
        s.setLinkedin(text(node, "linkedin"));
        s.setInstagram(text(node, "instagram"));
        s.setNotificationEmail(text(node, "notificationEmail"));
        s.setAboutTitle(text(node, "aboutTitle"));
        s.setAboutDescription(text(node, "aboutDescription"));
        s.setFeature1Title(text(node, "feature1Title"));
        s.setFeature1Description(text(node, "feature1Description"));
        s.setFeature2Title(text(node, "feature2Title"));
        s.setFeature2Description(text(node, "feature2Description"));
        s.setFeature3Title(text(node, "feature3Title"));
        s.setFeature3Description(text(node, "feature3Description"));
        s.setFeature4Title(text(node, "feature4Title"));
        s.setFeature4Description(text(node, "feature4Description"));
        return s;
    }

    /**
     * Dated relative to today, so the Upcoming tab is never empty on a fresh install
     * however long after this was written the project is first created.
     *
     * <p>In code rather than the seed file precisely because of that relative dating —
     * a JSON file cannot express "twelve days from whenever this first runs".
     */
    private static int seedEvents(EventRepository repo, LocalDate today) {
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
        return seeds.size();
    }

    /**
     * Sample achievements, so the section is not an empty box on day one.
     *
     * <p>These names are the association's real office-bearers, but the achievements
     * themselves are placeholders — replace them from the admin panel with real ones.
     */
    private static int seedAchievements(AchievementRepository repo, LocalDate today) {
        record Seed(String title, String student, String category, int daysAgo, String description) {}
        List<Seed> seeds = List.of(
                new Seed("Winner - Smart India Hackathon (Regional)", "Shreyash Gote", "competition", 45,
                        "Led a four-member team to first place with a crop-disease detector running entirely on-device."),
                new Seed("Paper accepted at ICACCI 2026", "Chinmay Deshpande", "research", 72,
                        "Co-authored \"Lightweight Transformers for Regional Language OCR\", accepted in the student track."),
                new Seed("Machine Learning Intern, Persistent Systems", "Srushti Halluri", "internship", 110,
                        "Six-month internship on the recommendation platform team, converted to a pre-placement offer."),
                new Seed("Best Paper - State Level Technical Symposium", "Pradnya Magdum", "research", 190,
                        "Recognised for work on federated learning across low-bandwidth rural clinics."));

        for (Seed seed : seeds) {
            Achievement achievement = new Achievement(seed.title(), seed.student());
            achievement.setCategory(seed.category());
            achievement.setAchievedOn(today.minusDays(seed.daysAgo()));
            achievement.setDescription(seed.description());
            repo.save(achievement);
        }
        return seeds.size();
    }
}
