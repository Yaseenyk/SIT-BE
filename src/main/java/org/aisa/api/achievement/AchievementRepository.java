package org.aisa.api.achievement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    /**
     * Newest first, with undated entries last rather than first.
     *
     * <p>Postgres sorts NULLs first on DESC by default, which would put every achievement
     * whose date nobody recorded above the ones that are actually recent.
     */
    @Query("""
            select a from Achievement a
            order by a.achievedOn desc nulls last, a.createdAt desc
            """)
    List<Achievement> findAllNewestFirst();

    @Query("""
            select a from Achievement a
            where a.category = :category
            order by a.achievedOn desc nulls last, a.createdAt desc
            """)
    List<Achievement> findByCategoryNewestFirst(String category);
}
