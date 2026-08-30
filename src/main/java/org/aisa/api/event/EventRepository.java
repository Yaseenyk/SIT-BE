package org.aisa.api.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Upcoming events, soonest first — the order a visitor deciding what to attend needs.
     * The comparison uses coalesce(ends_on, starts_on) so a multi-day event that has begun
     * but not finished still counts as upcoming.
     */
    @Query("""
            select e from Event e
            where coalesce(e.endsOn, e.startsOn) >= :today
            order by e.startsOn asc
            """)
    List<Event> findUpcoming(LocalDate today);

    /** Past events, most recent first — a reverse-chronological record. */
    @Query("""
            select e from Event e
            where coalesce(e.endsOn, e.startsOn) < :today
            order by e.startsOn desc
            """)
    List<Event> findPast(LocalDate today);

    List<Event> findAllByOrderByStartsOnDesc();

    @Query("select count(e) from Event e where coalesce(e.endsOn, e.startsOn) >= :today")
    long countUpcoming(LocalDate today);
}
