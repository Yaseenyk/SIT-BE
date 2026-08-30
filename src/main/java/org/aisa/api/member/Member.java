package org.aisa.api.member;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A student office-bearer.
 *
 * <p>{@code committeeId} holds the committee's immutable slug — its Firestore document id —
 * not its display name. Firestore has no foreign keys, so this is the closest thing to one,
 * and the choice of key is what matters: the original site stored the committee's NAME on
 * each member and matched on that string, so renaming a committee silently orphaned
 * everyone on it with no error anywhere.
 *
 * <p>The other half of a foreign key is what happens on delete. Firestore will not cascade
 * or null anything, so {@link org.aisa.api.committee.CommitteeService} explicitly clears
 * this field for every affected member in the same batched write that deletes the
 * committee — the equivalent of {@code ON DELETE SET NULL}, and covered by a test, because
 * nothing in the database will enforce it for us.
 */
@Getter
@Setter
@NoArgsConstructor
public class Member extends BaseEntity {

    private UUID id;

    private String name;

    private String role;

    /** The committee's slug, or null when the member is unassigned. */
    private String committeeId;

    private String academicYear;

    private String linkedinUrl;

    private String githubUrl;

    private String email;

    private String photoUrl;

    private String photoPublicId;

    private int displayOrder;

    public Member(String name, String role) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.role = role;
    }
}
