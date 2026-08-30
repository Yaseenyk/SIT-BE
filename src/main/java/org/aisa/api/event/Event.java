package org.aisa.api.event;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A workshop, hackathon, lecture or competition.
 *
 * <p>There is no {@code status} column. The old site stored events in two hardcoded
 * arrays and ran {@code autoSortEvents()} on every page load to move things between them
 * by parsing strings like {@code 'Oct 10-12, 2024'} — which meant the split depended on
 * someone opening the page, and a differently-formatted date stayed in the wrong list for
 * good. Here the date is a real date and the split is derived at read time.
 */
@Getter
@Setter
@NoArgsConstructor
public class Event extends BaseEntity {

    private static final DateTimeFormatter DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_ONLY =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private UUID id;

    private String title;

    private LocalDate startsOn;

    /** Set only for multi-day events. */
    private LocalDate endsOn;

    /**
     * An optional human-written label that overrides the generated one.
     *
     * <p>Exists so the imported strings from the old site ({@code 'Oct 10-12, 2024'}) are
     * preserved exactly, and so an admin can write "Every Friday in March" without the
     * schema needing a recurrence model it would use twice.
     */
    private String dateLabel;

    private String tag;

    private String emoji;

    private String description;

    private String linkUrl;

    private String bannerUrl;

    private String bannerPublicId;

    public Event(String title, LocalDate startsOn) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.startsOn = startsOn;
    }

    /**
     * An event counts as past only after its last day has finished, so a three-day
     * bootcamp stays under "Upcoming" while it is actually running.
     */
    public boolean isPast(LocalDate today) {
        LocalDate lastDay = endsOn != null ? endsOn : startsOn;
        return lastDay.isBefore(today);
    }

    /** The label to render: the admin's override if there is one, otherwise generated. */
    public String displayDate() {
        if (dateLabel != null && !dateLabel.isBlank()) {
            return dateLabel;
        }
        if (endsOn == null || endsOn.equals(startsOn)) {
            return startsOn.format(DAY_MONTH_YEAR);
        }
        // "Oct 10-12, 2024" within a month; "Oct 30 - Nov 2, 2024" across one.
        return endsOn.getMonth() == startsOn.getMonth() && endsOn.getYear() == startsOn.getYear()
                ? "%s-%d, %d".formatted(startsOn.format(DAY_ONLY), endsOn.getDayOfMonth(), endsOn.getYear())
                : "%s - %s".formatted(startsOn.format(DAY_ONLY), endsOn.format(DAY_MONTH_YEAR));
    }
}
