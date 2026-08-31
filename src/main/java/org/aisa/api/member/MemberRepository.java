package org.aisa.api.member;

import static org.aisa.api.firestore.Documents.intOr;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

    private final Firestore firestore;

    public MemberRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    /** Everyone, ordered. Unassigned members are included — see {@link #clearCommittee}. */
    public List<Member> findAllOrdered() {
        return all().stream()
                .sorted(Comparator.comparingInt(Member::getDisplayOrder).thenComparing(Member::getName))
                .toList();
    }

    public List<Member> findByCommittee(String committeeId) {
        return findAllOrdered().stream()
                .filter(member -> committeeId.equals(member.getCommitteeId()))
                .toList();
    }

    public Optional<Member> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.MEMBERS).document(id.toString()).get(),
                "reading member " + id);
        return doc.exists() ? Optional.of(toMember(doc)) : Optional.empty();
    }

    public long count() {
        return all().size();
    }

    public int nextDisplayOrder() {
        return all().stream().mapToInt(Member::getDisplayOrder).max().orElse(0) + 1;
    }

    /**
     * Member counts per committee.
     *
     * <p>Grouped in memory rather than with a Firestore aggregate. Firestore has no
     * {@code GROUP BY}, so the alternatives are one count query per committee — ten round
     * trips to render one page — or a counter field maintained on every member write, which
     * is a denormalisation that goes wrong the first time a write fails halfway.
     */
    public Map<String, Long> countGroupedByCommittee() {
        return all().stream()
                .filter(member -> member.getCommitteeId() != null)
                .collect(Collectors.groupingBy(Member::getCommitteeId, Collectors.counting()));
    }

    public Member save(Member member) {
        Instant now = Instant.now();
        if (member.getCreatedAt() == null) {
            member.setCreatedAt(now);
        }
        member.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.MEMBERS)
                .document(member.getId().toString())
                .set(toMap(member)), "saving member " + member.getId());
        return member;
    }

    public void deleteById(UUID id) {
        Fs.block(firestore.collection(Collections.MEMBERS).document(id.toString()).delete(),
                "deleting member " + id);
    }

    /**
     * Unassigns every member of a committee, in one batched write.
     *
     * <p>This is {@code ON DELETE SET NULL}, done by hand. Firestore enforces no referential
     * integrity whatsoever, so without this call deleting a committee would leave members
     * pointing at a document that no longer exists — they would vanish from the structure
     * page and from the committee filter, while still counting towards the member total.
     * A batch so that either all of them are unassigned or none are.
     *
     * @return how many members were unassigned
     */
    public int clearCommittee(String committeeId) {
        List<Member> affected = findByCommittee(committeeId);
        if (affected.isEmpty()) {
            return 0;
        }
        WriteBatch batch = firestore.batch();
        Instant now = Instant.now();
        for (Member member : affected) {
            member.setCommitteeId(null);
            member.setUpdatedAt(now);
            batch.set(firestore.collection(Collections.MEMBERS).document(member.getId().toString()),
                    toMap(member));
        }
        Fs.block(batch.commit(), "unassigning members of committee " + committeeId);
        return affected.size();
    }

    private List<Member> all() {
        return Fs.documents(firestore.collection(Collections.MEMBERS).get(), "reading members")
                .stream()
                .map(MemberRepository::toMember)
                .toList();
    }

    static Member toMember(DocumentSnapshot doc) {
        Member member = new Member();
        member.setId(UUID.fromString(doc.getId()));
        member.setName(str(doc, "name"));
        member.setRole(str(doc, "role"));
        member.setCommitteeId(str(doc, "committeeId"));
        member.setAcademicYear(str(doc, "academicYear"));
        member.setLinkedinUrl(str(doc, "linkedinUrl"));
        member.setGithubUrl(str(doc, "githubUrl"));
        member.setEmail(str(doc, "email"));
        member.setPhone(str(doc, "phone"));
        member.setPhotoUrl(str(doc, "photoUrl"));
        member.setPhotoPublicId(str(doc, "photoPublicId"));
        member.setDisplayOrder(intOr(doc, "displayOrder", 0));
        member.setCreatedAt(Documents.instant(doc, "createdAt"));
        member.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return member;
    }

    static Map<String, Object> toMap(Member m) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", m.getName());
        map.put("role", m.getRole());
        map.put("committeeId", m.getCommitteeId());
        map.put("academicYear", m.getAcademicYear());
        map.put("linkedinUrl", m.getLinkedinUrl());
        map.put("githubUrl", m.getGithubUrl());
        map.put("email", m.getEmail());
        map.put("phone", m.getPhone());
        map.put("photoUrl", m.getPhotoUrl());
        map.put("photoPublicId", m.getPhotoPublicId());
        map.put("displayOrder", m.getDisplayOrder());
        map.put("createdAt", Documents.toField(m.getCreatedAt()));
        map.put("updatedAt", Documents.toField(m.getUpdatedAt()));
        return map;
    }
}
