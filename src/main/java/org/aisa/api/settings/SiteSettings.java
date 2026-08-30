package org.aisa.api.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contact details, the About copy, the four feature blurbs, and the announcement bar.
 *
 * <p>A single row with a fixed id, guarded by a check constraint. Two documents in the old
 * Firestore version ({@code settings/site} and {@code settings/announcement}) meant two
 * round trips on every page load for data the public site always needs together.
 *
 * <p>Not extending BaseEntity: there is no meaningful "created" moment for a row that the
 * schema guarantees exists, so it carries only {@code updatedAt}.
 */
@Entity
@Table(name = "site_settings")
@Getter
@Setter
@NoArgsConstructor
public class SiteSettings {

    /** The only legal value. See the site_settings_singleton constraint. */
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(length = 64)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column
    private String address;

    @Column(length = 255)
    private String website;

    @Column(length = 500)
    private String linkedin;

    @Column(length = 500)
    private String instagram;

    /**
     * Where the "notify admin" link on the contact form points. Admin-only: exposing it on
     * the public settings endpoint would publish a staff address to every scraper.
     */
    @Column(name = "notification_email", length = 255)
    private String notificationEmail;

    @Column(name = "about_title", length = 200)
    private String aboutTitle;

    @Column(name = "about_description")
    private String aboutDescription;

    @Column(name = "feature1_title", length = 120)
    private String feature1Title;

    @Column(name = "feature1_description")
    private String feature1Description;

    @Column(name = "feature2_title", length = 120)
    private String feature2Title;

    @Column(name = "feature2_description")
    private String feature2Description;

    @Column(name = "feature3_title", length = 120)
    private String feature3Title;

    @Column(name = "feature3_description")
    private String feature3Description;

    @Column(name = "feature4_title", length = 120)
    private String feature4Title;

    @Column(name = "feature4_description")
    private String feature4Description;

    @Column(name = "announcement_text")
    private String announcementText;

    @Column(name = "announcement_expires_at")
    private Instant announcementExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** An announcement with no text, or one past its expiry, is not shown. */
    public boolean hasLiveAnnouncement(Instant now) {
        return announcementText != null
                && !announcementText.isBlank()
                && (announcementExpiresAt == null || announcementExpiresAt.isAfter(now));
    }
}
