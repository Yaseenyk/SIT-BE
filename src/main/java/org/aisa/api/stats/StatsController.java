package org.aisa.api.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.aisa.api.achievement.AchievementRepository;
import org.aisa.api.committee.CommitteeRepository;
import org.aisa.api.event.EventRepository;
import org.aisa.api.gallery.GalleryItemRepository;
import org.aisa.api.member.MemberRepository;
import org.aisa.api.message.ContactMessageRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The counters on the home page and the admin dashboard.
 *
 * <p>One endpoint rather than the frontend fetching six collections and calling
 * {@code .length} on each — which is what the old page did, downloading every gallery
 * image record to display the number 40.
 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Stats", description = "Counters for the home page and dashboard")
public class StatsController {

    private final CommitteeRepository committees;
    private final MemberRepository members;
    private final EventRepository events;
    private final GalleryItemRepository gallery;
    private final AchievementRepository achievements;
    private final ContactMessageRepository messages;
    private final Clock clock;

    public StatsController(
            CommitteeRepository committees,
            MemberRepository members,
            EventRepository events,
            GalleryItemRepository gallery,
            AchievementRepository achievements,
            ContactMessageRepository messages,
            Clock clock) {
        this.committees = committees;
        this.members = members;
        this.events = events;
        this.gallery = gallery;
        this.achievements = achievements;
        this.messages = messages;
        this.clock = clock;
    }

    public record PublicStats(
            long committees, long members, long events, long upcomingEvents,
            long photos, long achievements) {}

    /** Public stats plus the inbox counter the dashboard badge needs. */
    public record AdminStats(PublicStats counts, long unreadMessages) {}

    @GetMapping
    @Operation(summary = "Public counters")
    public PublicStats publicStats() {
        return counts();
    }

    @GetMapping("/admin")
    @Operation(summary = "Counters including the unread-message count")
    public AdminStats adminStats() {
        return new AdminStats(counts(), messages.countUnread());
    }

    private PublicStats counts() {
        LocalDate today = LocalDate.now(clock);
        return new PublicStats(
                committees.count(),
                members.count(),
                events.count(),
                events.countUpcoming(today),
                gallery.count(),
                achievements.count());
    }
}
