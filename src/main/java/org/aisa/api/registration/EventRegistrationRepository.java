package org.aisa.api.registration;

import static org.aisa.api.firestore.Documents.integer;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
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

/** Event sign-ups. */
@Repository
public class EventRegistrationRepository {

    private final Firestore firestore;

    public EventRegistrationRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    /** Oldest first — an attendance list reads in the order people signed up. */
    public List<EventRegistration> findByEvent(UUID eventId) {
        return Fs.documents(
                        firestore.collection(Collections.EVENT_REGISTRATIONS)
                                .whereEqualTo("eventId", eventId.toString())
                                .get(),
                        "reading registrations for event " + eventId)
                .stream()
                .map(EventRegistrationRepository::toRegistration)
                .sorted(Comparator.comparing(
                        EventRegistration::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<EventRegistration> findByUid(String uid) {
        return Fs.documents(
                        firestore.collection(Collections.EVENT_REGISTRATIONS)
                                .whereEqualTo("uid", uid)
                                .get(),
                        "reading registrations for " + uid)
                .stream()
                .map(EventRegistrationRepository::toRegistration)
                .toList();
    }

    public Optional<EventRegistration> find(UUID eventId, String uid) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.EVENT_REGISTRATIONS)
                        .document(EventRegistration.documentId(eventId, uid))
                        .get(),
                "reading a registration");
        return doc.exists() ? Optional.of(toRegistration(doc)) : Optional.empty();
    }

    /**
     * How many people are signed up for each event, in one read.
     *
     * <p>The events list shows a count per event; querying per event would be one round
     * trip per card. Registrations number in the hundreds at most, so reading them all
     * once and grouping in memory is both simpler and faster than N queries.
     */
    public Map<String, Long> countGroupedByEvent() {
        Map<String, Long> counts = new HashMap<>();
        for (QueryDocumentSnapshot doc :
                Fs.documents(firestore.collection(Collections.EVENT_REGISTRATIONS).get(),
                        "counting registrations")) {
            String eventId = doc.getString("eventId");
            if (eventId != null) {
                counts.merge(eventId, 1L, Long::sum);
            }
        }
        return counts;
    }

    public long count() {
        return Fs.documents(firestore.collection(Collections.EVENT_REGISTRATIONS).get(),
                "counting registrations").size();
    }

    public EventRegistration save(EventRegistration registration) {
        Instant now = Instant.now();
        if (registration.getCreatedAt() == null) {
            registration.setCreatedAt(now);
        }
        registration.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.EVENT_REGISTRATIONS)
                .document(registration.documentId())
                .set(toMap(registration)), "saving a registration");
        return registration;
    }

    public void delete(UUID eventId, String uid) {
        Fs.block(firestore.collection(Collections.EVENT_REGISTRATIONS)
                .document(EventRegistration.documentId(eventId, uid))
                .delete(), "cancelling a registration");
    }

    /**
     * Removes every registration for an event.
     *
     * <p>Called when the event itself is deleted. Firestore has no cascade, so this is the
     * cascade — without it, deleting an event leaves rows that point at nothing and the
     * dashboard counts people signed up for an event that no longer exists.
     */
    public int deleteByEvent(UUID eventId) {
        List<QueryDocumentSnapshot> docs = Fs.documents(
                firestore.collection(Collections.EVENT_REGISTRATIONS)
                        .whereEqualTo("eventId", eventId.toString())
                        .get(),
                "finding registrations to delete");
        if (docs.isEmpty()) {
            return 0;
        }
        // One batch, not a delete per document: a partial failure halfway through a loop
        // would leave exactly the orphans this exists to prevent.
        WriteBatch batch = firestore.batch();
        docs.forEach(doc -> batch.delete(doc.getReference()));
        Fs.block(batch.commit(), "deleting registrations for event " + eventId);
        return docs.size();
    }

    /** Called when an account is deleted, for the same reason as {@link #deleteByEvent}. */
    public int deleteByUid(String uid) {
        List<QueryDocumentSnapshot> docs = Fs.documents(
                firestore.collection(Collections.EVENT_REGISTRATIONS)
                        .whereEqualTo("uid", uid)
                        .get(),
                "finding registrations to delete");
        if (docs.isEmpty()) {
            return 0;
        }
        WriteBatch batch = firestore.batch();
        docs.forEach(doc -> batch.delete(doc.getReference()));
        Fs.block(batch.commit(), "deleting registrations for " + uid);
        return docs.size();
    }

    static EventRegistration toRegistration(DocumentSnapshot doc) {
        EventRegistration registration = new EventRegistration();
        String eventId = str(doc, "eventId");
        registration.setEventId(eventId == null ? null : UUID.fromString(eventId));
        registration.setUid(str(doc, "uid"));
        registration.setName(str(doc, "name"));
        registration.setEmail(str(doc, "email"));
        registration.setRollNumber(str(doc, "rollNumber"));
        registration.setYear(integer(doc, "year"));
        registration.setCreatedAt(Documents.instant(doc, "createdAt"));
        registration.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return registration;
    }

    static Map<String, Object> toMap(EventRegistration registration) {
        Map<String, Object> map = new HashMap<>();
        // Stored as a string as well as being half the document id: a document id is not
        // queryable as a field, so `whereEqualTo("eventId", …)` needs it written out.
        map.put("eventId", registration.getEventId().toString());
        map.put("uid", registration.getUid());
        map.put("name", registration.getName());
        map.put("email", registration.getEmail());
        map.put("rollNumber", registration.getRollNumber());
        map.put("year", registration.getYear());
        map.put("createdAt", Documents.toField(registration.getCreatedAt()));
        map.put("updatedAt", Documents.toField(registration.getUpdatedAt()));
        return map;
    }
}
