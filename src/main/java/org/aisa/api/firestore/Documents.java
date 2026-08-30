package org.aisa.api.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversions between Firestore field values and the types the domain uses.
 *
 * <p>Mapping is written by hand rather than left to the SDK's reflective POJO mapper. The
 * mapper is convenient right up to the moment a field is renamed or a type changes, at
 * which point it silently reads null instead of failing — and null is a legitimate value
 * for most fields here, so nothing would surface until a page rendered blank.
 *
 * <h2>Dates are ISO strings, on purpose</h2>
 *
 * A {@code LocalDate} is stored as {@code "2026-09-11"}. ISO-8601 dates sort
 * lexicographically in exactly the order they sort chronologically, so Firestore's string
 * range queries give correct date ranges with no extra machinery. Storing a Timestamp
 * instead would drag a timezone into a value that has no time in it — which is how the
 * original site ended up comparing an event date against the visitor's own clock.
 */
public final class Documents {

    private Documents() {
    }

    // ── Reads ────────────────────────────────────────────────────────────────────

    public static String str(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        // Firestore has no empty-string convention; treat "" as absent so callers get one
        // "nothing here" value rather than two.
        return value == null || value.isBlank() ? null : value;
    }

    public static int intOr(DocumentSnapshot doc, String field, int fallback) {
        Long value = doc.getLong(field);
        return value == null ? fallback : value.intValue();
    }

    public static Integer integer(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? null : value.intValue();
    }

    public static boolean bool(DocumentSnapshot doc, String field) {
        Boolean value = doc.getBoolean(field);
        return value != null && value;
    }

    public static LocalDate date(DocumentSnapshot doc, String field) {
        String value = str(doc, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            // A hand-edited document should not take the whole listing down.
            return null;
        }
    }

    public static Instant instant(DocumentSnapshot doc, String field) {
        Timestamp value = doc.getTimestamp(field);
        return value == null ? null : value.toDate().toInstant();
    }

    @SuppressWarnings("unchecked")
    public static List<String> strings(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    // ── Writes ───────────────────────────────────────────────────────────────────

    /** ISO-8601, so lexicographic ordering matches chronological ordering. */
    public static String toField(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static Timestamp toField(Instant instant) {
        return instant == null ? null : Timestamp.ofTimeSecondsAndNanos(
                instant.getEpochSecond(), instant.getNano());
    }

    /**
     * Blank becomes null.
     *
     * <p>An empty string from a cleared admin form means "no value". Storing "" would make
     * the frontend render an empty LinkedIn button that goes nowhere.
     */
    public static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
