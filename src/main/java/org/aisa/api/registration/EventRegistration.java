package org.aisa.api.registration;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * One student, signed up for one event.
 *
 * <h2>The document id is derived, not generated</h2>
 *
 * <p>{@code <eventId>__<uid>}. Firestore's {@code set} on an existing id overwrites rather
 * than duplicating, so a double-click, a retried request or a second browser tab all
 * converge on the same single row — without a read-then-write that two concurrent requests
 * could both pass. A generated id would need a uniqueness check that Firestore has no way
 * to enforce.
 *
 * <h2>Why the name and roll number are copied here</h2>
 *
 * <p>An attendance list has to say who attended. If it held only uids, then a student
 * deleting their account would empty the record of an event that already happened, and
 * every row would need a second lookup to render. These are a snapshot taken at the moment
 * of registering, which is exactly what an attendance sheet is.
 */
@Getter
@Setter
@NoArgsConstructor
public class EventRegistration extends BaseEntity {

    private UUID eventId;

    private String uid;

    private String name;

    private String email;

    private String rollNumber;

    private Integer year;

    public EventRegistration(UUID eventId, String uid, String name, String email) {
        this.eventId = eventId;
        this.uid = uid;
        this.name = name;
        this.email = email;
    }

    public String documentId() {
        return documentId(eventId, uid);
    }

    public static String documentId(UUID eventId, String uid) {
        return eventId + "__" + uid;
    }
}
