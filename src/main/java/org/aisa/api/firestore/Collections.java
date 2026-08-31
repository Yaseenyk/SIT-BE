package org.aisa.api.firestore;

/**
 * Every Firestore collection and document path in one place.
 *
 * <p>A typo in a collection name does not fail — Firestore happily creates it — so a
 * misspelling in one repository silently splits the data in two. Constants make that a
 * compile error instead.
 *
 * <p>The names match the original site's collections, so an existing project's data is
 * readable by this API without migration.
 */
public final class Collections {

    private Collections() {
    }

    public static final String COMMITTEES = "committees";
    public static final String MEMBERS = "members";
    public static final String EVENTS = "events";
    public static final String GALLERY = "gallery";
    public static final String ACHIEVEMENTS = "achievements";
    public static final String MESSAGES = "messages";
    public static final String SETTINGS = "settings";
    /**
     * Everyone with an account: students and admins alike, keyed by Firebase Auth uid.
     *
     * <p>This replaces {@code adminUsers}, which held a username and a BCrypt hash. Both
     * are now Firebase Auth's problem — this document holds only what Firebase does not
     * know about a person: their role, their year, their roll number, and whether an
     * admin has suspended them.
     */
    public static final String USERS = "users";

    public static final String EVENT_REGISTRATIONS = "eventRegistrations";

    public static final String COMMITTEE_APPLICATIONS = "committeeApplications";

    /**
     * Uploaded image bytes, one document each.
     *
     * <p>Separate from the records that reference them on purpose: a 700 KB field on a
     * gallery or member document would be pulled into memory by every listing query, which
     * is exactly the problem the original site had.
     */
    public static final String IMAGES = "images";

    /** The settings singleton. One document, as the public site always needs all of it. */
    public static final String SETTINGS_DOC = "site";
}
