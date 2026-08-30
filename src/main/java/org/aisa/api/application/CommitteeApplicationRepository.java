package org.aisa.api.application;

import static org.aisa.api.firestore.Documents.integer;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.aisa.api.application.CommitteeApplication.Status;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

/** Committee applications. */
@Repository
public class CommitteeApplicationRepository {

    private final Firestore firestore;

    public CommitteeApplicationRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<CommitteeApplication> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.COMMITTEE_APPLICATIONS).document(id.toString()).get(),
                "reading application " + id);
        return doc.exists() ? Optional.of(toApplication(doc)) : Optional.empty();
    }

    /** Newest first — the queue an admin works through is the queue of recent asks. */
    public List<CommitteeApplication> findAll() {
        return all().stream()
                .sorted(Comparator.comparing(
                        CommitteeApplication::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<CommitteeApplication> findByUid(String uid) {
        return Fs.documents(
                        firestore.collection(Collections.COMMITTEE_APPLICATIONS)
                                .whereEqualTo("uid", uid)
                                .get(),
                        "reading applications for " + uid)
                .stream()
                .map(CommitteeApplicationRepository::toApplication)
                .sorted(Comparator.comparing(
                        CommitteeApplication::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Whether this student already has an undecided application for this committee. */
    public boolean hasPending(String uid, String committeeId) {
        return findByUid(uid).stream()
                .anyMatch(application -> application.isPending()
                        && committeeId.equals(application.getCommitteeId()));
    }

    public long countPending() {
        return all().stream().filter(CommitteeApplication::isPending).count();
    }

    public CommitteeApplication save(CommitteeApplication application) {
        Instant now = Instant.now();
        if (application.getCreatedAt() == null) {
            application.setCreatedAt(now);
        }
        application.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.COMMITTEE_APPLICATIONS)
                .document(application.getId().toString())
                .set(toMap(application)), "saving application " + application.getId());
        return application;
    }

    public void deleteById(UUID id) {
        Fs.block(firestore.collection(Collections.COMMITTEE_APPLICATIONS)
                .document(id.toString()).delete(), "deleting application " + id);
    }

    /** Called when an account is deleted, so no application outlives its applicant. */
    public int deleteByUid(String uid) {
        List<QueryDocumentSnapshot> docs = Fs.documents(
                firestore.collection(Collections.COMMITTEE_APPLICATIONS)
                        .whereEqualTo("uid", uid).get(),
                "finding applications to delete");
        if (docs.isEmpty()) {
            return 0;
        }
        WriteBatch batch = firestore.batch();
        docs.forEach(doc -> batch.delete(doc.getReference()));
        Fs.block(batch.commit(), "deleting applications for " + uid);
        return docs.size();
    }

    /**
     * Called when a committee is deleted.
     *
     * <p>Deleting rather than unassigning, which is what {@code MemberRepository} does for
     * members. A member without a committee is still a person on the roster; an
     * application to a committee that no longer exists is nothing at all.
     */
    public int deleteByCommittee(String committeeId) {
        List<QueryDocumentSnapshot> docs = Fs.documents(
                firestore.collection(Collections.COMMITTEE_APPLICATIONS)
                        .whereEqualTo("committeeId", committeeId).get(),
                "finding applications to delete");
        if (docs.isEmpty()) {
            return 0;
        }
        WriteBatch batch = firestore.batch();
        docs.forEach(doc -> batch.delete(doc.getReference()));
        Fs.block(batch.commit(), "deleting applications for committee " + committeeId);
        return docs.size();
    }

    private List<CommitteeApplication> all() {
        return Fs.documents(firestore.collection(Collections.COMMITTEE_APPLICATIONS).get(),
                        "reading applications")
                .stream()
                .map(CommitteeApplicationRepository::toApplication)
                .toList();
    }

    static CommitteeApplication toApplication(DocumentSnapshot doc) {
        CommitteeApplication application = new CommitteeApplication();
        application.setId(UUID.fromString(doc.getId()));
        application.setUid(str(doc, "uid"));
        application.setApplicantName(str(doc, "applicantName"));
        application.setApplicantEmail(str(doc, "applicantEmail"));
        application.setRollNumber(str(doc, "rollNumber"));
        application.setYear(integer(doc, "year"));
        application.setCommitteeId(str(doc, "committeeId"));
        application.setMotivation(str(doc, "motivation"));
        application.setStatus(Status.parse(str(doc, "status")));
        application.setReviewedBy(str(doc, "reviewedBy"));
        application.setReviewedAt(Documents.instant(doc, "reviewedAt"));
        application.setCreatedAt(Documents.instant(doc, "createdAt"));
        application.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return application;
    }

    static Map<String, Object> toMap(CommitteeApplication application) {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", application.getUid());
        map.put("applicantName", application.getApplicantName());
        map.put("applicantEmail", application.getApplicantEmail());
        map.put("rollNumber", application.getRollNumber());
        map.put("year", application.getYear());
        map.put("committeeId", application.getCommitteeId());
        map.put("motivation", application.getMotivation());
        map.put("status", application.getStatus().name());
        map.put("reviewedBy", application.getReviewedBy());
        map.put("reviewedAt", Documents.toField(application.getReviewedAt()));
        map.put("createdAt", Documents.toField(application.getCreatedAt()));
        map.put("updatedAt", Documents.toField(application.getUpdatedAt()));
        return map;
    }
}
