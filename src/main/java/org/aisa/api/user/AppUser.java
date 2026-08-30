package org.aisa.api.user;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A person with an account — student or admin.
 *
 * <p>The document id is the Firebase Auth uid, which is what makes this record findable
 * from nothing but a verified token. No generated id of our own: a second identifier for
 * the same person is a second thing that can disagree.
 *
 * <h2>What is deliberately NOT here</h2>
 *
 * No password hash, no lockout counters, no "email verified" flag. Firebase Auth owns
 * credentials and the state that goes with them; duplicating any of it here would create
 * two answers to one question. This document holds only what Firebase has no opinion
 * about: the person's role on this site, their academic details, and whether an admin has
 * suspended them.
 *
 * <p>{@code email} and {@code name} ARE mirrored from the token, and that is a considered
 * exception: the admin user list and the attendee list for an event have to be readable
 * without a Firebase Auth lookup per row. They are refreshed on every sign-in.
 */
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends BaseEntity {

    /** The Firebase Auth uid. Also the document id. */
    private String uid;

    private String email;

    private String name;

    private UserRole role = UserRole.STUDENT;

    private AccountStatus status = AccountStatus.ACTIVE;

    private String rollNumber;

    /** Year of study, 1–4. Null until the student fills in their profile. */
    private Integer year;

    private String photoUrl;

    /** Cloudinary's handle for {@link #photoUrl}, so replacing a photo can release the old one. */
    private String photoPublicId;

    private Instant lastLoginAt;

    public AppUser(String uid, String email, String name) {
        this.uid = uid;
        this.email = email;
        this.name = name;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isSuspended() {
        return status == AccountStatus.SUSPENDED;
    }
}
