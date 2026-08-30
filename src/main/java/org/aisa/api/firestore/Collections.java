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
    public static final String ADMIN_USERS = "adminUsers";

    /** The settings singleton. One document, as the public site always needs all of it. */
    public static final String SETTINGS_DOC = "site";
}
