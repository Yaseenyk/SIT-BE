package org.aisa.api.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final SiteSettingsRepository repository;
    private final Clock clock;

    public SettingsService(SiteSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** What the public site reads. Deliberately excludes {@code notificationEmail}. */
    public record PublicSettings(
            String phone,
            String email,
            String address,
            String website,
            String linkedin,
            String instagram,
            String aboutTitle,
            String aboutDescription,
            Feature[] features,
            Announcement announcement) {}

    public record Feature(String title, String description) {}

    public record Announcement(String text, Instant expiresAt) {}

    /** The admin view — the public fields plus the notification address. */
    public record AdminSettings(
            PublicSettings publicSettings,
            String notificationEmail,
            Instant updatedAt) {}

    public record SettingsRequest(
            @Size(max = 64) String phone,
            @Email(message = "That does not look like an email address") @Size(max = 255) String email,
            String address,
            @Size(max = 255) String website,
            @Size(max = 500) String linkedin,
            @Size(max = 500) String instagram,
            @Email(message = "That does not look like an email address") @Size(max = 255) String notificationEmail,
            @Size(max = 200) String aboutTitle,
            String aboutDescription,
            @Size(max = 120) String feature1Title, String feature1Description,
            @Size(max = 120) String feature2Title, String feature2Description,
            @Size(max = 120) String feature3Title, String feature3Description,
            @Size(max = 120) String feature4Title, String feature4Description) {}

    public record AnnouncementRequest(
            @NotBlank(message = "Announcement text is required") @Size(max = 500) String text,
            /** Null means it runs until removed by hand. */
            Instant expiresAt) {}

    public PublicSettings getPublic() {
        return toPublic(load());
    }

    public AdminSettings getForAdmin() {
        SiteSettings settings = load();
        return new AdminSettings(toPublic(settings), settings.getNotificationEmail(), settings.getUpdatedAt());
    }

    public AdminSettings update(SettingsRequest request) {
        SiteSettings settings = load();
        settings.setPhone(blankToNull(request.phone()));
        settings.setEmail(blankToNull(request.email()));
        settings.setAddress(blankToNull(request.address()));
        settings.setWebsite(blankToNull(request.website()));
        settings.setLinkedin(blankToNull(request.linkedin()));
        settings.setInstagram(blankToNull(request.instagram()));
        settings.setNotificationEmail(blankToNull(request.notificationEmail()));
        settings.setAboutTitle(blankToNull(request.aboutTitle()));
        settings.setAboutDescription(blankToNull(request.aboutDescription()));
        settings.setFeature1Title(blankToNull(request.feature1Title()));
        settings.setFeature1Description(blankToNull(request.feature1Description()));
        settings.setFeature2Title(blankToNull(request.feature2Title()));
        settings.setFeature2Description(blankToNull(request.feature2Description()));
        settings.setFeature3Title(blankToNull(request.feature3Title()));
        settings.setFeature3Description(blankToNull(request.feature3Description()));
        settings.setFeature4Title(blankToNull(request.feature4Title()));
        settings.setFeature4Description(blankToNull(request.feature4Description()));
        settings.setUpdatedAt(clock.instant());
        repository.save(settings);
        return getForAdmin();
    }

    public Announcement setAnnouncement(AnnouncementRequest request) {
        SiteSettings settings = load();
        settings.setAnnouncementText(request.text().trim());
        settings.setAnnouncementExpiresAt(request.expiresAt());
        settings.setUpdatedAt(clock.instant());
        repository.save(settings);
        return new Announcement(settings.getAnnouncementText(), settings.getAnnouncementExpiresAt());
    }

    public void clearAnnouncement() {
        SiteSettings settings = load();
        settings.setAnnouncementText(null);
        settings.setAnnouncementExpiresAt(null);
        settings.setUpdatedAt(clock.instant());
        repository.save(settings);
    }

    /**
     * Loads the settings document, creating an empty one if it is missing.
     *
     * <p>The seeder writes it on first boot, so this rarely fires. It matters more here
     * than it did on SQL: a relational schema could guarantee the row existed, whereas
     * Firestore has no such thing as a required document — the collection is simply empty
     * until something writes to it. Returning an empty settings object keeps the public
     * site rendering (with no phone number) instead of failing every page load.
     */
    private SiteSettings load() {
        return repository.find().orElseGet(() -> repository.save(new SiteSettings()));
    }

    private PublicSettings toPublic(SiteSettings s) {
        Feature[] features = {
                new Feature(s.getFeature1Title(), s.getFeature1Description()),
                new Feature(s.getFeature2Title(), s.getFeature2Description()),
                new Feature(s.getFeature3Title(), s.getFeature3Description()),
                new Feature(s.getFeature4Title(), s.getFeature4Description()),
        };
        // Expiry is applied here, not in the browser. The old site compared the expiry
        // against the visitor's own clock, so a device with the wrong date kept showing an
        // announcement that had ended weeks earlier.
        Announcement announcement = s.hasLiveAnnouncement(clock.instant())
                ? new Announcement(s.getAnnouncementText(), s.getAnnouncementExpiresAt())
                : null;

        return new PublicSettings(
                s.getPhone(), s.getEmail(), s.getAddress(), s.getWebsite(),
                s.getLinkedin(), s.getInstagram(),
                s.getAboutTitle(), s.getAboutDescription(),
                features, announcement);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
