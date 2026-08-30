package org.aisa.api.member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aisa.api.committee.Committee;
import org.aisa.api.committee.CommitteeRepository;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.media.MediaService;
import org.aisa.api.member.MemberDtos.MemberRequest;
import org.aisa.api.member.MemberDtos.MemberResponse;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberRepository members;
    private final CommitteeRepository committees;
    private final MediaService media;

    public MemberService(MemberRepository members, CommitteeRepository committees, MediaService media) {
        this.members = members;
        this.committees = committees;
        this.media = media;
    }

    public List<MemberResponse> findAll(String committeeId) {
        List<Member> found = (committeeId == null || committeeId.isBlank())
                ? members.findAllOrdered()
                : members.findByCommittee(committeeId);

        /*
         * The committee NAME is resolved for the response, not stored on the member.
         *
         * Firestore has no joins, so this is the join — one read of the committees
         * collection, then a lookup per member. Denormalising the name onto each member
         * document would avoid it and would also recreate the original site's bug exactly:
         * renaming a committee would leave every member advertising the old name.
         */
        Map<String, String> names = committeeNames();
        return found.stream().map(member -> toResponse(member, names)).toList();
    }

    public MemberResponse findById(UUID id) {
        return toResponse(require(id), committeeNames());
    }

    public MemberResponse create(MemberRequest request) {
        Member member = new Member(request.name().trim(), request.role().trim());
        member.setDisplayOrder(members.nextDisplayOrder());
        apply(member, request);
        return toResponse(members.save(member), committeeNames());
    }

    public MemberResponse update(UUID id, MemberRequest request) {
        Member member = require(id);
        // A replaced photo leaves the old Cloudinary asset unreferenced; release it now
        // rather than accumulating orphans nobody can find later.
        if (member.getPhotoPublicId() != null
                && !member.getPhotoPublicId().equals(request.photoPublicId())) {
            media.deleteQuietly(member.getPhotoPublicId());
        }
        member.setName(request.name().trim());
        member.setRole(request.role().trim());
        apply(member, request);
        return toResponse(members.save(member), committeeNames());
    }

    public void delete(UUID id) {
        Member member = require(id);
        media.deleteQuietly(member.getPhotoPublicId());
        members.deleteById(id);
    }

    private Member require(UUID id) {
        return members.findById(id).orElseThrow(() -> new NotFoundException("Member", id));
    }

    private Map<String, String> committeeNames() {
        Map<String, String> names = new HashMap<>();
        for (Committee committee : committees.findAllOrdered()) {
            names.put(committee.getId(), committee.getName());
        }
        return names;
    }

    private void apply(Member member, MemberRequest request) {
        String committeeId = blankToNull(request.committeeId());
        if (committeeId != null && !committees.existsById(committeeId)) {
            /*
             * Checked here because Firestore will not check it for us. Storing an id that
             * matches no committee would leave the member invisible on the structure page
             * while still counting towards the member total — the silent-orphan failure
             * this design exists to prevent.
             */
            throw new NotFoundException("Committee", committeeId);
        }
        member.setCommitteeId(committeeId);
        member.setAcademicYear(blankToNull(request.academicYear()));
        member.setLinkedinUrl(blankToNull(request.linkedinUrl()));
        member.setGithubUrl(blankToNull(request.githubUrl()));
        member.setEmail(blankToNull(request.email()));
        member.setPhotoUrl(blankToNull(request.photoUrl()));
        member.setPhotoPublicId(blankToNull(request.photoPublicId()));
    }

    /**
     * An empty string from a cleared form field means "no value", not "the empty string".
     * Storing "" would make the frontend render an empty LinkedIn button that goes nowhere.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MemberResponse toResponse(Member m, Map<String, String> committeeNames) {
        String committeeId = m.getCommitteeId();
        return new MemberResponse(
                m.getId(),
                m.getName(),
                m.getRole(),
                committeeId,
                // Null when the committee was deleted — the frontend renders "Unassigned".
                committeeId == null ? null : committeeNames.get(committeeId),
                m.getAcademicYear(),
                m.getLinkedinUrl(),
                m.getGithubUrl(),
                m.getEmail(),
                m.getPhotoUrl(),
                m.getDisplayOrder());
    }
}
