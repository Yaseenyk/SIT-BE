package org.aisa.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The upcoming/past split and the date label.
 *
 * <p>A plain unit test — no Spring, no database. This is pure date arithmetic, and the
 * behaviour it replaces ({@code autoSortEvents} parsing 'Oct 10-12, 2024' with a regex)
 * is exactly the part of the old site that got it wrong.
 */
class EventDisplayTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 3, 15);

    @Test
    void aSingleDayEventIsPastOnlyAfterItsDay() {
        Event yesterday = new Event("Quiz", TODAY.minusDays(1));
        Event today = new Event("Quiz", TODAY);
        Event tomorrow = new Event("Quiz", TODAY.plusDays(1));

        assertThat(yesterday.isPast(TODAY)).isTrue();
        // An event happening today is still upcoming — it has not finished yet.
        assertThat(today.isPast(TODAY)).isFalse();
        assertThat(tomorrow.isPast(TODAY)).isFalse();
    }

    @Test
    void aMultiDayEventStaysUpcomingWhileItIsRunning() {
        Event bootcamp = new Event("Bootcamp", TODAY.minusDays(2));
        bootcamp.setEndsOn(TODAY.plusDays(1));

        assertThat(bootcamp.isPast(TODAY)).isFalse();

        bootcamp.setEndsOn(TODAY.minusDays(1));
        assertThat(bootcamp.isPast(TODAY)).isTrue();
    }

    @Test
    void singleDayEventsRenderAFullDate() {
        assertThat(new Event("Symposium", LocalDate.of(2025, 1, 18)).displayDate())
                .isEqualTo("Jan 18, 2025");
    }

    @Test
    void multiDayEventsWithinOneMonthCollapseTheMonth() {
        Event bootcamp = new Event("Bootcamp", LocalDate.of(2024, 10, 10));
        bootcamp.setEndsOn(LocalDate.of(2024, 10, 12));

        assertThat(bootcamp.displayDate()).isEqualTo("Oct 10-12, 2024");
    }

    @Test
    void multiDayEventsSpanningMonthsSpellBothOut() {
        Event fest = new Event("Fest", LocalDate.of(2024, 10, 30));
        fest.setEndsOn(LocalDate.of(2024, 11, 2));

        assertThat(fest.displayDate()).isEqualTo("Oct 30 - Nov 2, 2024");
    }

    @Test
    void anAdminWrittenLabelWins() {
        Event recurring = new Event("Study group", LocalDate.of(2025, 3, 7));
        recurring.setDateLabel("Every Friday in March");

        assertThat(recurring.displayDate()).isEqualTo("Every Friday in March");
    }
}
