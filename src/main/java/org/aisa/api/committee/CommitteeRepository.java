package org.aisa.api.committee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommitteeRepository extends JpaRepository<Committee, String> {

    List<Committee> findAllByOrderByDisplayOrderAsc();

    List<Committee> findByTypeOrderByDisplayOrderAsc(String type);

    /** Next free slot, used when creating a committee so it lands at the end of the list. */
    @Query("select coalesce(max(c.displayOrder), 0) + 1 from Committee c")
    int nextDisplayOrder();

    /** The neighbour to swap with when an admin moves a committee up or down. */
    @Query("""
            select c from Committee c
            where c.displayOrder < :order
            order by c.displayOrder desc
            limit 1
            """)
    Optional<Committee> findPrevious(int order);

    @Query("""
            select c from Committee c
            where c.displayOrder > :order
            order by c.displayOrder asc
            limit 1
            """)
    Optional<Committee> findNext(int order);
}
