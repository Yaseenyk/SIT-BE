package org.aisa.api.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.aisa.api.application.ApplicationDtos.ApplicationSummary;
import org.aisa.api.application.ApplicationDtos.ApplyRequest;
import org.aisa.api.application.ApplicationDtos.MyApplication;
import org.aisa.api.application.ApplicationDtos.ReviewRequest;
import org.aisa.api.application.CommitteeApplication.Status;
import org.aisa.api.committee.Committee;
import org.aisa.api.committee.CommitteeRepository;
import org.aisa.api.common.ConflictException;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.member.Member;
import org.aisa.api.member.MemberRepository;
import org.aisa.api.security.CurrentUser;
import org.aisa.api.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Applying to a committee, and the admin review that follows. */
@Service
public class CommitteeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CommitteeApplicationService.class);

    private final CommitteeApplicationRepository applications;
    private final CommitteeRepository committees;
    private final MemberRepository members;

    public CommitteeApplicationService(
            CommitteeApplicationRepository applications,
            CommitteeRepository committees,
            MemberRepository members) {
        this.applications = applications;
        this.committees = committees;
        this.members = members;
    }

    // ── Student ──────────────────────────────────────────────────────────────────

    public MyApplication apply(ApplyRequest request) {
        AppUser student = CurrentUser.requireProfile();

        Committee committee = committees.findById(request.committeeId())
                .orElseThrow(() -> new NotFoundException("Committee", request.committeeId()));

        /*
         * One undecided application per committee. Not one ever: a student rejected last
         * year should be able to apply again this year, and blocking that would make a
         * single decision permanent.
         */
        if (applications.hasPending(student.getUid(), committee.getId())) {
            throw new ConflictException(
                    "You already have an application for " + committee.getName() + " awaiting review.");
        }

        CommitteeApplication application = new CommitteeApplication(
                student.getUid(), committee.getId(), request.motivation().trim());
        application.setApplicantName(student.getName());
        application.setApplicantEmail(student.getEmail());
        application.setRollNumber(student.getRollNumber());
        application.setYear(student.getYear());
        applications.save(application);

        log.info("{} applied to committee {}", student.getUid(), committee.getId());
        return toMine(application, committee.getName());
    }

    public List<MyApplication> mine() {
        String uid = CurrentUser.requireUid();
        Map<String, String> names = committeeNames();
        return applications.findByUid(uid).stream()
                .map(application -> toMine(application, names.get(application.getCommitteeId())))
                .toList();
    }

    /** Withdrawing is deleting, and only while the decision is still open. */
    public void withdraw(UUID id) {
        String uid = CurrentUser.requireUid();
        CommitteeApplication application = applications.findById(id)
                .orElseThrow(() -> new NotFoundException("Application", id));
        if (!uid.equals(application.getUid())) {
            // 404 rather than 403: confirming that an id exists would let one student
            // enumerate other people's applications.
            throw new NotFoundException("Application", id);
        }
        if (!application.isPending()) {
            throw new ConflictException("This application has already been reviewed.");
        }
        applications.deleteById(id);
    }

    // ── Admin ────────────────────────────────────────────────────────────────────

    public List<ApplicationSummary> list(String status) {
        Map<String, String> names = committeeNames();
        Status wanted = status == null || status.isBlank() ? null : Status.parse(status);
        return applications.findAll().stream()
                .filter(application -> wanted == null || application.getStatus() == wanted)
                .map(application -> toSummary(application, names.get(application.getCommitteeId())))
                .toList();
    }

    /**
     * Accepts or rejects an application.
     *
     * <p>Accepting also puts the student on the roster, which is the point of the feature —
     * the alternative is an admin who accepts an application and then has to retype the
     * same person into the members panel, and inevitably does not.
     */
    public ApplicationSummary review(UUID id, ReviewRequest request) {
        String reviewer = CurrentUser.requireUid();
        CommitteeApplication application = applications.findById(id)
                .orElseThrow(() -> new NotFoundException("Application", id));

        Status decision = Status.parse(request.status());
        if (decision == Status.PENDING) {
            throw new ConflictException("A review must be either ACCEPTED or REJECTED.");
        }

        application.setStatus(decision);
        application.setReviewedBy(reviewer);
        application.setReviewedAt(Instant.now());
        applications.save(application);

        if (decision == Status.ACCEPTED) {
            addToRoster(application, request.role());
        }

        log.info("{} {} application {}", reviewer, decision, id);
        return toSummary(application, committeeNames().get(application.getCommitteeId()));
    }

    /**
     * Adds the accepted applicant to the committee's member list.
     *
     * <p>Skipped if someone with the same email is already on the roster, so accepting the
     * same person twice — or accepting an application from a student who is already an
     * office-bearer — does not produce a duplicate row on the public site.
     */
    private void addToRoster(CommitteeApplication application, String role) {
        String email = application.getApplicantEmail();
        boolean alreadyListed = email != null && members.findAllOrdered().stream()
                .anyMatch(member -> email.equalsIgnoreCase(member.getEmail()));
        if (alreadyListed) {
            log.info("Not adding {} to the roster: already listed", email);
            return;
        }

        Member member = new Member(
                application.getApplicantName(),
                role == null || role.isBlank() ? "Member" : role.trim());
        member.setCommitteeId(application.getCommitteeId());
        member.setEmail(email);
        member.setDisplayOrder(members.nextDisplayOrder());
        members.save(member);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────────

    private Map<String, String> committeeNames() {
        return committees.findAllOrdered().stream()
                .collect(Collectors.toMap(Committee::getId, Committee::getName, (a, b) -> a));
    }

    private static MyApplication toMine(CommitteeApplication application, String committeeName) {
        return new MyApplication(
                application.getId(),
                application.getCommitteeId(),
                committeeName,
                application.getMotivation(),
                application.getStatus().name(),
                application.getCreatedAt(),
                application.getReviewedAt());
    }

    private static ApplicationSummary toSummary(
            CommitteeApplication application, String committeeName) {
        return new ApplicationSummary(
                application.getId(),
                application.getUid(),
                application.getApplicantName(),
                application.getApplicantEmail(),
                application.getRollNumber(),
                application.getYear(),
                application.getCommitteeId(),
                committeeName,
                application.getMotivation(),
                application.getStatus().name(),
                application.getCreatedAt(),
                application.getReviewedAt());
    }
}
