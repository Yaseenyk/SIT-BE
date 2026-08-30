package org.aisa.api.auth;

import static org.aisa.api.firestore.Documents.intOr;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

/**
 * Admin accounts.
 *
 * <p>Note what is stored: a BCrypt hash, plus the lockout counters. The original site used
 * Firebase Auth and kept the username in a world-readable Firestore document; here the
 * whole record sits in a collection the security rules deny to every client, reachable
 * only through this API with the service-account credential.
 */
@Repository
public class AdminUserRepository {

    private final Firestore firestore;

    public AdminUserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Case-insensitive, matching the {@code findByUsernameIgnoreCase} it replaces.
     *
     * <p>Filtered in memory: Firestore has no case-insensitive comparison, and the usual
     * workaround — storing a lowercased duplicate of the field purely to query on — is a
     * second copy that can disagree with the first. There is one admin account.
     */
    public Optional<AdminUser> findByUsernameIgnoreCase(String username) {
        return all().stream()
                .filter(admin -> admin.getUsername() != null
                        && admin.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    public boolean existsByUsernameIgnoreCase(String username) {
        return findByUsernameIgnoreCase(username).isPresent();
    }

    public Optional<AdminUser> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.ADMIN_USERS).document(id.toString()).get(),
                "reading admin " + id);
        return doc.exists() ? Optional.of(toAdmin(doc)) : Optional.empty();
    }

    public long count() {
        return all().size();
    }

    public AdminUser save(AdminUser admin) {
        Instant now = Instant.now();
        if (admin.getCreatedAt() == null) {
            admin.setCreatedAt(now);
        }
        admin.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.ADMIN_USERS)
                .document(admin.getId().toString())
                .set(toMap(admin)), "saving admin " + admin.getId());
        return admin;
    }

    private List<AdminUser> all() {
        return Fs.documents(firestore.collection(Collections.ADMIN_USERS).get(), "reading admins")
                .stream()
                .map(AdminUserRepository::toAdmin)
                .toList();
    }

    static AdminUser toAdmin(DocumentSnapshot doc) {
        AdminUser admin = new AdminUser();
        admin.setId(UUID.fromString(doc.getId()));
        admin.setUsername(str(doc, "username"));
        admin.setPasswordHash(str(doc, "passwordHash"));
        admin.setFailedAttempts(intOr(doc, "failedAttempts", 0));
        admin.setLockedUntil(Documents.instant(doc, "lockedUntil"));
        admin.setLastLoginAt(Documents.instant(doc, "lastLoginAt"));
        admin.setCreatedAt(Documents.instant(doc, "createdAt"));
        admin.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return admin;
    }

    static Map<String, Object> toMap(AdminUser a) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", a.getUsername());
        map.put("passwordHash", a.getPasswordHash());
        map.put("failedAttempts", a.getFailedAttempts());
        map.put("lockedUntil", Documents.toField(a.getLockedUntil()));
        map.put("lastLoginAt", Documents.toField(a.getLastLoginAt()));
        map.put("createdAt", Documents.toField(a.getCreatedAt()));
        map.put("updatedAt", Documents.toField(a.getUpdatedAt()));
        return map;
    }
}
