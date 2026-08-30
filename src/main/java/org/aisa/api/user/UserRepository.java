package org.aisa.api.user;

import static org.aisa.api.firestore.Documents.integer;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
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

/** Accounts, keyed by Firebase Auth uid. */
@Repository
public class UserRepository {

    private final Firestore firestore;

    public UserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<AppUser> findByUid(String uid) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.USERS).document(uid).get(), "reading user " + uid);
        return doc.exists() ? Optional.of(toUser(doc)) : Optional.empty();
    }

    /**
     * Filtered in memory rather than with a {@code whereEqualTo}.
     *
     * <p>Firestore's equality is case-sensitive and addresses are not: signing up as
     * {@code Yaseen@bsiet.org} and again as {@code yaseen@bsiet.org} would otherwise pass
     * the duplicate check and produce two accounts for one person. Firebase Auth would
     * reject the second signup anyway, so this is the belt to that braces — and the user
     * list is small enough that reading it is cheaper than maintaining a lowercased copy
     * of the field purely to query on.
     */
    public Optional<AppUser> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return all().stream()
                .filter(user -> email.equalsIgnoreCase(user.getEmail()))
                .findFirst();
    }

    /** Newest first — an admin reviewing accounts wants the people who just signed up. */
    public List<AppUser> findAll() {
        return all().stream()
                .sorted(Comparator.comparing(
                        AppUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<AppUser> findByRole(UserRole role) {
        return all().stream().filter(user -> user.getRole() == role).toList();
    }

    public long countAdmins() {
        return all().stream().filter(AppUser::isAdmin).count();
    }

    public AppUser save(AppUser user) {
        Instant now = Instant.now();
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        user.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.USERS).document(user.getUid()).set(toMap(user)),
                "saving user " + user.getUid());
        return user;
    }

    public void deleteByUid(String uid) {
        Fs.block(firestore.collection(Collections.USERS).document(uid).delete(),
                "deleting user " + uid);
    }

    private List<AppUser> all() {
        return Fs.documents(firestore.collection(Collections.USERS).get(), "reading users").stream()
                .map(UserRepository::toUser)
                .toList();
    }

    static AppUser toUser(DocumentSnapshot doc) {
        AppUser user = new AppUser();
        user.setUid(doc.getId());
        user.setEmail(str(doc, "email"));
        user.setName(str(doc, "name"));
        user.setRole(UserRole.parse(str(doc, "role")));
        user.setStatus(AccountStatus.parse(str(doc, "status")));
        user.setRollNumber(str(doc, "rollNumber"));
        user.setYear(integer(doc, "year"));
        user.setPhotoUrl(str(doc, "photoUrl"));
        user.setPhotoPublicId(str(doc, "photoPublicId"));
        user.setLastLoginAt(Documents.instant(doc, "lastLoginAt"));
        user.setCreatedAt(Documents.instant(doc, "createdAt"));
        user.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return user;
    }

    static Map<String, Object> toMap(AppUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("email", user.getEmail());
        map.put("name", user.getName());
        // Stored as the enum name. `role` is the single most security-relevant field in
        // the database, so it is written in the one form UserRole.parse round-trips.
        map.put("role", user.getRole().name());
        map.put("status", user.getStatus().name());
        map.put("rollNumber", user.getRollNumber());
        map.put("year", user.getYear());
        map.put("photoUrl", user.getPhotoUrl());
        map.put("photoPublicId", user.getPhotoPublicId());
        map.put("lastLoginAt", Documents.toField(user.getLastLoginAt()));
        map.put("createdAt", Documents.toField(user.getCreatedAt()));
        map.put("updatedAt", Documents.toField(user.getUpdatedAt()));
        return map;
    }
}
