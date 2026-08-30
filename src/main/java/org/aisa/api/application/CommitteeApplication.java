package org.aisa.api.application;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A student asking to join a committee.
 *
 * <p>Unlike {@link org.aisa.api.registration.EventRegistration}, this has a generated id
 * rather than one derived from the student and the committee. A rejected application must
 * not block a later one — a derived id would mean re-applying overwrites the record of the
 * first decision, and the committee would lose the history it needs to make the second.
 */
@Getter
@Setter
@NoArgsConstructor
public class CommitteeApplication extends BaseEntity {

    /** The application's status. */
    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED;

        public static Status parse(String value) {
            if (value == null) {
                return PENDING;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return PENDING;
            }
        }
    }

    private UUID id;

    private String uid;

    /** Snapshotted, for the same reason an attendance list snapshots them. */
    private String applicantName;

    private String applicantEmail;

    private String rollNumber;

    private Integer year;

    private String committeeId;

    private String motivation;

    private Status status = Status.PENDING;

    /** The uid of the admin who decided. Kept so a contested decision has an author. */
    private String reviewedBy;

    private Instant reviewedAt;

    public CommitteeApplication(String uid, String committeeId, String motivation) {
        this.id = UUID.randomUUID();
        this.uid = uid;
        this.committeeId = committeeId;
        this.motivation = motivation;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}
