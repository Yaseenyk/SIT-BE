package org.aisa.api.common;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * The timestamps every document carries.
 *
 * <p>Set explicitly by the repositories on save rather than by a framework listener. JPA
 * auditing filled these automatically; Firestore has no equivalent, and a server-side
 * {@code FieldValue.serverTimestamp()} would leave the in-memory object holding null until
 * it was re-read — so the API would return null for a field it had just written.
 */
@Getter
@Setter
public abstract class BaseEntity {

    private Instant createdAt;

    private Instant updatedAt;
}
