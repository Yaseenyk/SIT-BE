package org.aisa.api.committee;

import static org.aisa.api.firestore.Documents.intOr;
import static org.aisa.api.firestore.Documents.str;
import static org.aisa.api.firestore.Documents.strings;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

/**
 * Committees, in Firestore.
 *
 * <p>The document id is the committee's slug, so a lookup by id is a direct document get
 * rather than a query — and the slug is what the public site's {@code #committee-x} links
 * already use.
 *
 * <p>Ordering and "next free display order" are computed <em>in memory</em> after fetching
 * the collection. That is a deliberate choice, not laziness: there are ten committees. A
 * Firestore {@code orderBy} would need a composite index for every combination the admin
 * screen offers, and an aggregate for the max would be a second round trip. Sorting ten
 * documents in Java costs microseconds and needs no index maintenance.
 */
@Repository
public class CommitteeRepository {

    private final Firestore firestore;

    public CommitteeRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<Committee> findAllOrdered() {
        return all().stream()
                .sorted(Comparator.comparingInt(Committee::getDisplayOrder))
                .toList();
    }

    public List<Committee> findByType(String type) {
        return findAllOrdered().stream()
                .filter(committee -> type.equals(committee.getType()))
                .toList();
    }

    public Optional<Committee> findById(String id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.COMMITTEES).document(id).get(),
                "reading committee " + id);
        return doc.exists() ? Optional.of(toCommittee(doc)) : Optional.empty();
    }

    public boolean existsById(String id) {
        return findById(id).isPresent();
    }

    public long count() {
        return all().size();
    }

    public int nextDisplayOrder() {
        return all().stream().mapToInt(Committee::getDisplayOrder).max().orElse(0) + 1;
    }

    /** The neighbour to swap with when an admin moves a committee up. */
    public Optional<Committee> findPrevious(int order) {
        return all().stream()
                .filter(committee -> committee.getDisplayOrder() < order)
                .max(Comparator.comparingInt(Committee::getDisplayOrder));
    }

    public Optional<Committee> findNext(int order) {
        return all().stream()
                .filter(committee -> committee.getDisplayOrder() > order)
                .min(Comparator.comparingInt(Committee::getDisplayOrder));
    }

    public Committee save(Committee committee) {
        Instant now = Instant.now();
        if (committee.getCreatedAt() == null) {
            committee.setCreatedAt(now);
        }
        committee.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.COMMITTEES)
                .document(committee.getId())
                .set(toMap(committee)), "saving committee " + committee.getId());
        return committee;
    }

    /** Used by the reorder swap, so both committees move in one atomic write. */
    public void saveAll(List<Committee> committees) {
        WriteBatch batch = firestore.batch();
        Instant now = Instant.now();
        for (Committee committee : committees) {
            if (committee.getCreatedAt() == null) {
                committee.setCreatedAt(now);
            }
            committee.setUpdatedAt(now);
            batch.set(firestore.collection(Collections.COMMITTEES).document(committee.getId()),
                    toMap(committee));
        }
        Fs.block(batch.commit(), "reordering committees");
    }

    public void deleteById(String id) {
        Fs.block(firestore.collection(Collections.COMMITTEES).document(id).delete(), "deleting committee " + id);
    }

    private List<Committee> all() {
        return Fs.documents(firestore.collection(Collections.COMMITTEES).get(), "reading committees")
                .stream()
                .map(CommitteeRepository::toCommittee)
                .toList();
    }

    static Committee toCommittee(DocumentSnapshot doc) {
        Committee committee = new Committee(doc.getId());
        committee.setDisplayOrder(intOr(doc, "displayOrder", 0));
        committee.setType(str(doc, "type"));
        committee.setName(str(doc, "name"));
        committee.setIcon(str(doc, "icon"));
        committee.setGradient(str(doc, "gradient"));
        committee.setSizeLabel(str(doc, "sizeLabel"));
        committee.setBadge(str(doc, "badge"));
        committee.setCoordLabel(str(doc, "coordLabel"));
        committee.setCoordinator(str(doc, "coordinator"));
        committee.setCoordinatorSub(str(doc, "coordinatorSub"));
        committee.setCoordinatorPhoto(str(doc, "coordinatorPhoto"));
        committee.setCoordinatorPhotoId(str(doc, "coordinatorPhotoId"));
        committee.setCoord2Label(str(doc, "coord2Label"));
        committee.setCoordinator2(str(doc, "coordinator2"));
        committee.setCoordinator2Photo(str(doc, "coordinator2Photo"));
        committee.setCoordinator2PhotoId(str(doc, "coordinator2PhotoId"));
        committee.setResponsibilities(strings(doc, "responsibilities"));
        committee.setCreatedAt(Documents.instant(doc, "createdAt"));
        committee.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return committee;
    }

    static Map<String, Object> toMap(Committee c) {
        Map<String, Object> map = new HashMap<>();
        map.put("displayOrder", c.getDisplayOrder());
        map.put("type", c.getType());
        map.put("name", c.getName());
        map.put("icon", c.getIcon());
        map.put("gradient", c.getGradient());
        map.put("sizeLabel", c.getSizeLabel());
        map.put("badge", c.getBadge());
        map.put("coordLabel", c.getCoordLabel());
        map.put("coordinator", c.getCoordinator());
        map.put("coordinatorSub", c.getCoordinatorSub());
        map.put("coordinatorPhoto", c.getCoordinatorPhoto());
        map.put("coordinatorPhotoId", c.getCoordinatorPhotoId());
        map.put("coord2Label", c.getCoord2Label());
        map.put("coordinator2", c.getCoordinator2());
        map.put("coordinator2Photo", c.getCoordinator2Photo());
        map.put("coordinator2PhotoId", c.getCoordinator2PhotoId());
        map.put("responsibilities", List.copyOf(c.getResponsibilities()));
        map.put("createdAt", Documents.toField(c.getCreatedAt()));
        map.put("updatedAt", Documents.toField(c.getUpdatedAt()));
        return map;
    }

}
