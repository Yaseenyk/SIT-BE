package org.aisa.api.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.aisa.api.FirestoreIntegrationTest;
import org.aisa.api.committee.CommitteeDtos.CommitteeRequest;
import org.aisa.api.committee.CommitteeService;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.member.MemberDtos.MemberRequest;
import org.aisa.api.member.MemberDtos.MemberResponse;
import org.aisa.api.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The referential integrity Firestore does not provide.
 *
 * <p>This is the most important test in the codebase after the security rules. On Postgres
 * these guarantees were declared once — a foreign key with {@code ON DELETE SET NULL} — and
 * the database enforced them for every caller, forever. Firestore enforces nothing, so they
 * are now application code, and application code can be deleted by accident.
 *
 * <p>The failure being guarded against is the exact bug the original site had: a member
 * pointing at a committee that no longer exists, which disappears from the structure page
 * while still counting towards the member total. Nothing throws. Nobody notices.
 */
class ReferentialIntegrityTest extends FirestoreIntegrationTest {

    @Autowired
    private CommitteeService committees;

    @Autowired
    private MemberService members;

    private static CommitteeRequest committee(String id, String name) {
        return new CommitteeRequest(id, name, "functional", "⚙️", null, "4-6 students",
                "Functional", null, null, null, null, null, null, null, null, null,
                List.of("Do the thing"));
    }

    private static MemberRequest member(String name, String committeeId) {
        // linkedin, github, email, phone, photoUrl, photoPublicId
        return new MemberRequest(name, "Member", committeeId, "3rd Year",
                null, null, null, null, null, null);
    }

    @Test
    void deletingACommitteeUnassignsItsMembersRatherThanOrphaningThem() {
        committees.create(committee("technical", "Technical Committee"));
        members.create(member("Isha Sawant", "technical"));
        members.create(member("Tanvi Gaikwad", "technical"));

        assertThat(members.findAll("technical")).hasSize(2);

        committees.delete("technical");

        List<MemberResponse> all = members.findAll(null);
        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(m -> {
            // The SQL equivalent was ON DELETE SET NULL. Both fields must be clear: an id
            // left pointing at a deleted document is precisely the orphan state.
            assertThat(m.committeeId()).isNull();
            assertThat(m.committeeName()).isNull();
        });
    }

    @Test
    void deletingACommitteeLeavesOtherCommitteesMembersAlone() {
        committees.create(committee("technical", "Technical Committee"));
        committees.create(committee("media", "Media Committee"));
        members.create(member("Isha Sawant", "technical"));
        members.create(member("Riya Naik", "media"));

        committees.delete("technical");

        assertThat(members.findAll("media")).hasSize(1);
        assertThat(members.findAll("media").getFirst().committeeName()).isEqualTo("Media Committee");
    }

    /**
     * The other half: an id that never existed must be refused at write time. Firestore
     * would accept it silently, and the member would be invisible from the moment it was
     * created rather than from the moment a committee was deleted.
     */
    @Test
    void aMemberCannotBeAssignedToACommitteeThatDoesNotExist() {
        assertThatThrownBy(() -> members.create(member("Ghost", "no-such-committee")))
                .isInstanceOf(NotFoundException.class);

        assertThat(members.findAll(null)).isEmpty();
    }

    /**
     * The bug this whole design exists to prevent: the original site matched members to
     * committees by display NAME, so a rename detached every one of them.
     */
    @Test
    void renamingACommitteeKeepsItsMembersAttached() {
        committees.create(committee("technical", "Technical Committee"));
        members.create(member("Isha Sawant", "technical"));

        committees.update("technical", committee("technical", "Technical & Innovation Committee"));

        List<MemberResponse> attached = members.findAll("technical");
        assertThat(attached).hasSize(1);
        assertThat(attached.getFirst().committeeName()).isEqualTo("Technical & Innovation Committee");
    }

    @Test
    void memberCountsFollowTheCommittee() {
        committees.create(committee("technical", "Technical Committee"));
        members.create(member("Isha Sawant", "technical"));
        members.create(member("Tanvi Gaikwad", "technical"));

        assertThat(committees.findById("technical").memberCount()).isEqualTo(2);

        committees.delete("technical");
        committees.create(committee("technical", "Technical Committee"));

        // The members were unassigned, so a committee recreated under the same id starts
        // empty rather than silently inheriting the old one's people.
        assertThat(committees.findById("technical").memberCount()).isZero();
    }
}
