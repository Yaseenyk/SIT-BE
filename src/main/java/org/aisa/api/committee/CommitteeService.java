package org.aisa.api.committee;

import java.util.List;
import java.util.Map;
import org.aisa.api.committee.CommitteeDtos.CommitteeRequest;
import org.aisa.api.committee.CommitteeDtos.CommitteeResponse;
import org.aisa.api.common.ConflictException;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.media.MediaService;
import org.aisa.api.member.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommitteeService {

    private static final Logger log = LoggerFactory.getLogger(CommitteeService.class);

    private final CommitteeRepository committees;
    private final MemberRepository members;
    private final MediaService media;

    public CommitteeService(
            CommitteeRepository committees, MemberRepository members, MediaService media) {
        this.committees = committees;
        this.members = members;
        this.media = media;
    }

    public List<CommitteeResponse> findAll(String type) {
        List<Committee> found = (type == null || type.isBlank() || "all".equalsIgnoreCase(type))
                ? committees.findAllOrdered()
                : committees.findByType(type);

        /*
         * One grouped count for the whole page rather than a count per committee. The
         * structure section renders every committee at once, so the per-row version is
         * ten extra queries on the site's most-visited view.
         */
        Map<String, Long> memberCounts = members.countGroupedByCommittee();
        return found.stream().map(c -> toResponse(c, memberCounts)).toList();
    }

    public CommitteeResponse findById(String id) {
        Committee committee = require(id);
        return toResponse(committee, members.countGroupedByCommittee());
    }

    public CommitteeResponse create(CommitteeRequest request) {
        if (committees.existsById(request.id())) {
            throw new ConflictException("A committee with the id '" + request.id() + "' already exists");
        }
        Committee committee = new Committee(request.id());
        committee.setDisplayOrder(committees.nextDisplayOrder());
        apply(committee, request);
        return toResponse(committees.save(committee), members.countGroupedByCommittee());
    }

    public CommitteeResponse update(String id, CommitteeRequest request) {
        Committee committee = require(id);
        /*
         * The id is the URL fragment the public site links to, so it is immutable once
         * created. Allowing a rename here would mean either breaking shared links or
         * maintaining a redirect table for a ten-row table.
         */
        if (!committee.getId().equals(request.id())) {
            throw new ConflictException("A committee id cannot be changed after it is created");
        }
        replacePhotoIfChanged(committee.getCoordinatorPhotoId(), request.coordinatorPhotoId());
        replacePhotoIfChanged(committee.getCoordinator2PhotoId(), request.coordinator2PhotoId());
        apply(committee, request);
        return toResponse(committees.save(committee), members.countGroupedByCommittee());
    }

    /**
     * Deletes a committee and unassigns its members.
     *
     * <p>The unassignment is the {@code ON DELETE SET NULL} the relational schema declared
     * and Firestore cannot. It is done FIRST and deliberately: if the members batch fails,
     * the committee still exists and the data is consistent, whereas deleting the committee
     * first and then failing would leave members referencing a document that is gone.
     *
     * <p>The two writes are not atomic with each other — Firestore batches cannot span the
     * rollback of a preceding one — so the ordering is the guarantee. Worst case is a
     * committee whose members are already unassigned, which is visible and repairable;
     * the reverse would be invisible.
     */
    public void delete(String id) {
        Committee committee = require(id);
        int unassigned = members.clearCommittee(id);

        // Members survive; their photos belong to them, so only the committee's own
        // coordinator photos are released here.
        media.deleteQuietly(committee.getCoordinatorPhotoId());
        media.deleteQuietly(committee.getCoordinator2PhotoId());
        committees.deleteById(id);

        if (unassigned > 0) {
            log.info("Deleted committee '{}' and unassigned {} member(s).", id, unassigned);
        }
    }

    /**
     * Swaps a committee with its neighbour.
     *
     * <p>A swap rather than a renumber: the alternative — assigning a new index to every
     * row on each move — rewrites the whole table for a one-position change, and two
     * admins reordering at once would leave the sequence with duplicates.
     */
    public List<CommitteeResponse> move(String id, String direction) {
        Committee committee = require(id);
        Committee neighbour = ("up".equals(direction)
                ? committees.findPrevious(committee.getDisplayOrder())
                : committees.findNext(committee.getDisplayOrder()))
                .orElse(null);

        if (neighbour != null) {
            int swap = committee.getDisplayOrder();
            committee.setDisplayOrder(neighbour.getDisplayOrder());
            neighbour.setDisplayOrder(swap);
            committees.saveAll(List.of(committee, neighbour));
        }
        // Already at the end: not an error. The dashboard disables the button, and a 409
        // for a no-op click would be noise.
        return findAll(null);
    }

    private Committee require(String id) {
        return committees.findById(id).orElseThrow(() -> new NotFoundException("Committee", id));
    }

    /** Frees the old Cloudinary asset when a photo is swapped, so uploads do not accumulate. */
    private void replacePhotoIfChanged(String existingPublicId, String incomingPublicId) {
        if (existingPublicId != null && !existingPublicId.equals(incomingPublicId)) {
            media.deleteQuietly(existingPublicId);
        }
    }

    private void apply(Committee committee, CommitteeRequest request) {
        committee.setName(request.name());
        committee.setType(request.type());
        committee.setIcon(request.icon());
        committee.setGradient(request.gradient());
        committee.setSizeLabel(request.sizeLabel());
        committee.setBadge(request.badge());
        committee.setCoordLabel(request.coordLabel());
        committee.setCoordinator(request.coordinator());
        committee.setCoordinatorSub(request.coordinatorSub());
        committee.setCoordinatorPhoto(request.coordinatorPhoto());
        committee.setCoordinatorPhotoId(request.coordinatorPhotoId());
        committee.setCoord2Label(request.coord2Label());
        committee.setCoordinator2(request.coordinator2());
        committee.setCoordinator2Photo(request.coordinator2Photo());
        committee.setCoordinator2PhotoId(request.coordinator2PhotoId());
        committee.setResponsibilities(request.responsibilities() == null ? List.of() : request.responsibilities());
    }

    private CommitteeResponse toResponse(Committee c, Map<String, Long> memberCounts) {
        return new CommitteeResponse(
                c.getId(),
                c.getDisplayOrder(),
                c.getType(),
                c.getName(),
                c.getIcon(),
                c.getGradient(),
                c.getSizeLabel(),
                c.getBadge(),
                c.getCoordLabel(),
                c.getCoordinator(),
                c.getCoordinatorSub(),
                c.getCoordinatorPhoto(),
                c.getCoord2Label(),
                c.getCoordinator2(),
                c.getCoordinator2Photo(),
                List.copyOf(c.getResponsibilities()),
                memberCounts.getOrDefault(c.getId(), 0L).intValue());
    }
}
