package org.aisa.api.event;

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

/**
 * Events, in Firestore.
 *
 * <h2>How upcoming/past survives the move off SQL</h2>
 *
 * In Postgres this was {@code where coalesce(ends_on, starts_on) >= today} — one expression,
 * evaluated per query, incapable of going stale. Firestore cannot compare two fields to each
 * other, so that expression has no direct translation.
 *
 * <p>The naive fix is a stored {@code status} field kept current by a scheduled job. That is
 * precisely the original site's {@code autoSortEvents()} bug rebuilt: a value that is correct
 * only as often as something remembers to update it.
 *
 * <p>So instead {@link #lastDay} is computed and written <b>on every save</b> as
 * {@code endsOn ?? startsOn}. It is derived from data in the same document, at write time,
 * so it cannot drift from the fields it is derived from — there is no clock and no job
 * involved. The query is then a plain range on one field, which Firestore does natively:
 * {@code where lastDay >= today}. Dates are ISO strings, whose lexicographic order is their
 * chronological order.
 */
@Repository
public class EventRepository {

    private final Firestore firestore;

    public EventRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    /** Soonest first — the order someone deciding what to attend needs. */
    public List<Event> findUpcoming(LocalDate today) {
        return all().stream()
                .filter(event -> !event.isPast(today))
                .sorted(Comparator.comparing(Event::getStartsOn))
                .toList();
    }

    /** Most recent first — a reverse-chronological record. */
    public List<Event> findPast(LocalDate today) {
        return all().stream()
                .filter(event -> event.isPast(today))
                .sorted(Comparator.comparing(Event::getStartsOn).reversed())
                .toList();
    }

    public List<Event> findAllNewestFirst() {
        return all().stream()
                .sorted(Comparator.comparing(Event::getStartsOn).reversed())
                .toList();
    }

    public Optional<Event> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.EVENTS).document(id.toString()).get(),
                "reading event " + id);
        return doc.exists() ? Optional.of(toEvent(doc)) : Optional.empty();
    }

    public long count() {
        return all().size();
    }

    public long countUpcoming(LocalDate today) {
        return all().stream().filter(event -> !event.isPast(today)).count();
    }

    public Event save(Event event) {
        Instant now = Instant.now();
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(now);
        }
        event.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.EVENTS)
                .document(event.getId().toString())
                .set(toMap(event)), "saving event " + event.getId());
        return event;
    }

    public void deleteById(UUID id) {
        Fs.block(firestore.collection(Collections.EVENTS).document(id.toString()).delete(),
                "deleting event " + id);
    }

    /*
     * The whole collection is read and filtered in memory. At this size — a student
     * association runs tens of events, not tens of thousands — that is one round trip
     * instead of two, and it keeps `lastDay` as a stored field for correctness rather than
     * as an index Firestore must maintain. If this ever grows past a few hundred documents,
     * switch these to `whereGreaterThanOrEqualTo("lastDay", today.toString())`, which the
     * stored field already supports without a schema change.
     */
    private List<Event> all() {
        return Fs.documents(firestore.collection(Collections.EVENTS).get(), "reading events")
                .stream()
                .map(EventRepository::toEvent)
                .toList();
    }

    static Event toEvent(DocumentSnapshot doc) {
        Event event = new Event();
        event.setId(UUID.fromString(doc.getId()));
        event.setTitle(str(doc, "title"));
        event.setStartsOn(Documents.date(doc, "startsOn"));
        event.setEndsOn(Documents.date(doc, "endsOn"));
        event.setDateLabel(str(doc, "dateLabel"));
        event.setTag(str(doc, "tag"));
        event.setEmoji(str(doc, "emoji"));
        event.setDescription(str(doc, "description"));
        event.setLinkUrl(str(doc, "linkUrl"));
        event.setBannerUrl(str(doc, "bannerUrl"));
        event.setBannerPublicId(str(doc, "bannerPublicId"));
        event.setCreatedAt(Documents.instant(doc, "createdAt"));
        event.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return event;
    }

    static Map<String, Object> toMap(Event e) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", e.getTitle());
        map.put("startsOn", Documents.toField(e.getStartsOn()));
        map.put("endsOn", Documents.toField(e.getEndsOn()));
        /*
         * The derived field. Written here and nowhere else, so it is impossible to save an
         * event whose lastDay disagrees with its dates.
         */
        map.put("lastDay", Documents.toField(e.getEndsOn() != null ? e.getEndsOn() : e.getStartsOn()));
        map.put("dateLabel", e.getDateLabel());
        map.put("tag", e.getTag());
        map.put("emoji", e.getEmoji());
        map.put("description", e.getDescription());
        map.put("linkUrl", e.getLinkUrl());
        map.put("bannerUrl", e.getBannerUrl());
        map.put("bannerPublicId", e.getBannerPublicId());
        map.put("createdAt", Documents.toField(e.getCreatedAt()));
        map.put("updatedAt", Documents.toField(e.getUpdatedAt()));
        return map;
    }
}
