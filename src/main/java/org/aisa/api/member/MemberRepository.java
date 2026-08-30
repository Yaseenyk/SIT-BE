package org.aisa.api.member;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    /**
     * The whole roster with committees already attached.
     *
     * <p>{@code left join fetch} rather than a plain findAll: every row renders its
     * committee name, and unassigned members (committee_id is null, after a committee was
     * deleted) must still appear — an inner join would silently hide them.
     */
    @Query("""
            select m from Member m
            left join fetch m.committee
            order by m.displayOrder asc, m.name asc
            """)
    List<Member> findAllWithCommittee();

    @Query("""
            select m from Member m
            left join fetch m.committee c
            where c.id = :committeeId
            order by m.displayOrder asc, m.name asc
            """)
    List<Member> findByCommittee(String committeeId);

    @Query("""
            select m from Member m
            left join fetch m.committee
            where m.id = :id
            """)
    Optional<Member> findByIdWithCommittee(UUID id);

    @Query("select coalesce(max(m.displayOrder), 0) + 1 from Member m")
    int nextDisplayOrder();

    @Query("select m.committee.id, count(m) from Member m where m.committee is not null group by m.committee.id")
    List<Object[]> countByCommitteeRaw();

    /**
     * Member counts keyed by committee id.
     *
     * <p>A default method over the raw projection so callers get a Map instead of an
     * Object[] they have to index by position.
     */
    default Map<String, Long> countGroupedByCommittee() {
        return countByCommitteeRaw().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }
}
