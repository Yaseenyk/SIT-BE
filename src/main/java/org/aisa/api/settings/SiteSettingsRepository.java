package org.aisa.api.settings;

import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

/**
 * The settings singleton — one document at {@code settings/site}.
 *
 * <p>The original Firebase site split this across {@code settings/site} and
 * {@code settings/announcement}, so every page load made two round trips for data the
 * public site always needs together. One document, one read.
 */
@Repository
public class SiteSettingsRepository {

    private final Firestore firestore;

    public SiteSettingsRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<SiteSettings> find() {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.SETTINGS).document(Collections.SETTINGS_DOC).get(),
                "reading site settings");
        return doc.exists() ? Optional.of(toSettings(doc)) : Optional.empty();
    }

    public SiteSettings save(SiteSettings settings) {
        settings.setUpdatedAt(Instant.now());
        Fs.block(firestore.collection(Collections.SETTINGS)
                .document(Collections.SETTINGS_DOC)
                .set(toMap(settings)), "saving site settings");
        return settings;
    }

    static SiteSettings toSettings(DocumentSnapshot doc) {
        SiteSettings s = new SiteSettings();
        s.setPhone(str(doc, "phone"));
        s.setEmail(str(doc, "email"));
        s.setAddress(str(doc, "address"));
        s.setWebsite(str(doc, "website"));
        s.setLinkedin(str(doc, "linkedin"));
        s.setInstagram(str(doc, "instagram"));
        s.setNotificationEmail(str(doc, "notificationEmail"));
        s.setAboutTitle(str(doc, "aboutTitle"));
        s.setAboutDescription(str(doc, "aboutDescription"));
        s.setFeature1Title(str(doc, "feature1Title"));
        s.setFeature1Description(str(doc, "feature1Description"));
        s.setFeature2Title(str(doc, "feature2Title"));
        s.setFeature2Description(str(doc, "feature2Description"));
        s.setFeature3Title(str(doc, "feature3Title"));
        s.setFeature3Description(str(doc, "feature3Description"));
        s.setFeature4Title(str(doc, "feature4Title"));
        s.setFeature4Description(str(doc, "feature4Description"));
        s.setAnnouncementText(str(doc, "announcementText"));
        s.setAnnouncementExpiresAt(Documents.instant(doc, "announcementExpiresAt"));
        Instant updated = Documents.instant(doc, "updatedAt");
        s.setUpdatedAt(updated == null ? Instant.now() : updated);
        return s;
    }

    static Map<String, Object> toMap(SiteSettings s) {
        Map<String, Object> map = new HashMap<>();
        map.put("phone", s.getPhone());
        map.put("email", s.getEmail());
        map.put("address", s.getAddress());
        map.put("website", s.getWebsite());
        map.put("linkedin", s.getLinkedin());
        map.put("instagram", s.getInstagram());
        map.put("notificationEmail", s.getNotificationEmail());
        map.put("aboutTitle", s.getAboutTitle());
        map.put("aboutDescription", s.getAboutDescription());
        map.put("feature1Title", s.getFeature1Title());
        map.put("feature1Description", s.getFeature1Description());
        map.put("feature2Title", s.getFeature2Title());
        map.put("feature2Description", s.getFeature2Description());
        map.put("feature3Title", s.getFeature3Title());
        map.put("feature3Description", s.getFeature3Description());
        map.put("feature4Title", s.getFeature4Title());
        map.put("feature4Description", s.getFeature4Description());
        map.put("announcementText", s.getAnnouncementText());
        map.put("announcementExpiresAt", Documents.toField(s.getAnnouncementExpiresAt()));
        map.put("updatedAt", Documents.toField(s.getUpdatedAt()));
        return map;
    }
}
