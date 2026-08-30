package org.aisa.api.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
public class Event extends BaseEntity {

    private static final DateTimeFormatter DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_ONLY =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    /** Set only for multi-day events. */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    /**
     * An optional human-written label that overrides the generated one.
     *
     * <p>Exists so the imported strings from the old site ({@code 'Oct 10-12, 2024'}) are
     * preserved exactly, and so an admin can write "Every Friday in March" without the
     * schema needing a recurrence model it would use twice.
     */
    @Column(name = "date_label", length = 120)
    private String dateLabel;

    @Column(length = 64)
    private String tag;

    @Column(length = 16)
    private String emoji;

    @Column
    private String description;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "banner_public_id", length = 255)
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
