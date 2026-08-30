package org.aisa.api.achievement;

import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

@Repository
public class AchievementRepository {

    private final Firestore firestore;

    public AchievementRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<Achievement> findAllNewestFirst() {
        return all().stream().sorted(newestFirst()).toList();
    }

    public List<Achievement> findByCategory(String category) {
        return all().stream()
                .filter(achievement -> category.equals(achievement.getCategory()))
                .sorted(newestFirst())
                .toList();
    }

    public Optional<Achievement> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.ACHIEVEMENTS).document(id.toString()).get(),
                "reading achievement " + id);
        return doc.exists() ? Optional.of(toAchievement(doc)) : Optional.empty();
    }

    public long count() {
        return all().size();
    }

    public Achievement save(Achievement achievement) {
        Instant now = Instant.now();
        if (achievement.getCreatedAt() == null) {
            achievement.setCreatedAt(now);
        }
        achievement.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.ACHIEVEMENTS)
                .document(achievement.getId().toString())
                .set(toMap(achievement)), "saving achievement " + achievement.getId());
        return achievement;
    }

    public void deleteById(UUID id) {
        Fs.block(firestore.collection(Collections.ACHIEVEMENTS).document(id.toString()).delete(),
                "deleting achievement " + id);
    }

    /**
     * Newest first, undated last.
     *
     * <p>This was {@code order by achieved_on desc nulls last} in SQL. Firestore cannot
     * express it — worse, {@code orderBy("achievedOn")} would <b>omit every document that
     * has no achievedOn at all</b>, so an achievement saved without a date would silently
     * disappear from the site rather than sort to the bottom. Ordering in memory with an
     * explicit nulls-last comparator is the only version that cannot lose a record.
     */
    private static Comparator<Achievement> newestFirst() {
        Comparator<Achievement> byDate = Comparator.comparing(
                Achievement::getAchievedOn,
                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()));
        return byDate.thenComparing(
                Achievement::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<Achievement> all() {
        return Fs.documents(firestore.collection(Collections.ACHIEVEMENTS).get(), "reading achievements")
                .stream()
                .map(AchievementRepository::toAchievement)
                .toList();
    }

    static Achievement toAchievement(DocumentSnapshot doc) {
        Achievement achievement = new Achievement();
        achievement.setId(UUID.fromString(doc.getId()));
        achievement.setTitle(str(doc, "title"));
        achievement.setStudent(str(doc, "student"));
        achievement.setCategory(str(doc, "category"));
        achievement.setAchievedOn(Documents.date(doc, "achievedOn"));
        achievement.setDescription(str(doc, "description"));
        achievement.setPhotoUrl(str(doc, "photoUrl"));
        achievement.setPhotoPublicId(str(doc, "photoPublicId"));
        achievement.setCreatedAt(Documents.instant(doc, "createdAt"));
        achievement.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return achievement;
    }

    static Map<String, Object> toMap(Achievement a) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", a.getTitle());
        map.put("student", a.getStudent());
        map.put("category", a.getCategory());
        map.put("achievedOn", Documents.toField(a.getAchievedOn()));
        map.put("description", a.getDescription());
        map.put("photoUrl", a.getPhotoUrl());
        map.put("photoPublicId", a.getPhotoPublicId());
        map.put("createdAt", Documents.toField(a.getCreatedAt()));
        map.put("updatedAt", Documents.toField(a.getUpdatedAt()));
        return map;
    }
}
