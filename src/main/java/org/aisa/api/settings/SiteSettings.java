package org.aisa.api.settings;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contact details, the About copy, the four feature blurbs, and the announcement bar.
 *
 * <p>One document, at {@code settings/site}. The original Firebase site split this across
 * {@code settings/site} and {@code settings/announcement}, so every page load made two
 * round trips for data the public site always needs together.
 *
 * <p>Not extending BaseEntity: there is no meaningful "created" moment for a document that
 * is written once and thereafter only edited, so it carries only {@code updatedAt}.
 */
@Getter
@Setter
@NoArgsConstructor
public class SiteSettings {

    private String phone;

    private String email;

    private String address;

    private String website;

    private String linkedin;

    private String instagram;

    /**
     * Where the "notify admin" link on the contact form points. Admin-only: exposing it on
     * the public settings endpoint would publish a staff address to every scraper.
     */
    private String notificationEmail;

    private String aboutTitle;

    private String aboutDescription;

    private String feature1Title;

    private String feature1Description;

    private String feature2Title;

    private String feature2Description;

    private String feature3Title;

    private String feature3Description;

    private String feature4Title;

    private String feature4Description;

    private String announcementText;

    private Instant announcementExpiresAt;

    private Instant updatedAt = Instant.now();

    /** An announcement with no text, or one past its expiry, is not shown. */
    public boolean hasLiveAnnouncement(Instant now) {
        return announcementText != null
                && !announcementText.isBlank()
                && (announcementExpiresAt == null || announcementExpiresAt.isAfter(now));
    }
}
