package org.aisa.api.member;

import java.util.List;
import java.util.UUID;
import org.aisa.api.committee.Committee;
import org.aisa.api.committee.CommitteeRepository;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.media.MediaService;
import org.aisa.api.member.MemberDtos.MemberRequest;
import org.aisa.api.member.MemberDtos.MemberResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<MemberResponse> findAll(String committeeId) {
        List<Member> found = (committeeId == null || committeeId.isBlank())
                ? members.findAllWithCommittee()
                : members.findByCommittee(committeeId);
        return found.stream().map(MemberService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse findById(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        Member member = new Member(request.name().trim(), request.role().trim());
        member.setDisplayOrder(members.nextDisplayOrder());
        apply(member, request);
        return toResponse(members.save(member));
    }

    @Transactional
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
        return toResponse(members.save(member));
    }

    @Transactional
    public void delete(UUID id) {
        Member member = require(id);
        media.deleteQuietly(member.getPhotoPublicId());
        members.delete(member);
    }

    private Member require(UUID id) {
        return members.findByIdWithCommittee(id).orElseThrow(() -> new NotFoundException("Member", id));
    }

    private void apply(Member member, MemberRequest request) {
        Committee committee = null;
        if (request.committeeId() != null && !request.committeeId().isBlank()) {
            committee = committees.findById(request.committeeId())
                    // Fail rather than silently leaving the member unassigned: a typo in
                    // the dropdown value would otherwise look like a successful save.
                    .orElseThrow(() -> new NotFoundException("Committee", request.committeeId()));
        }
        member.setCommittee(committee);
        member.setAcademicYear(request.academicYear());
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

    private static MemberResponse toResponse(Member m) {
        Committee committee = m.getCommittee();
        return new MemberResponse(
                m.getId(),
                m.getName(),
                m.getRole(),
                committee == null ? null : committee.getId(),
                committee == null ? null : committee.getName(),
                m.getAcademicYear(),
                m.getLinkedinUrl(),
                m.getGithubUrl(),
                m.getEmail(),
                m.getPhotoUrl(),
                m.getDisplayOrder());
    }
}
