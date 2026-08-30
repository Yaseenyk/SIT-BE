package org.aisa.api.message;

import static org.aisa.api.firestore.Documents.bool;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

@Repository
public class ContactMessageRepository {

    private final Firestore firestore;

    public ContactMessageRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<ContactMessage> findAllNewestFirst() {
        return all().stream()
                .sorted(Comparator.comparing(
                        ContactMessage::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Optional<ContactMessage> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.MESSAGES).document(id.toString()).get(),
                "reading message " + id);
        return doc.exists() ? Optional.of(toMessage(doc)) : Optional.empty();
    }

    public long countUnread() {
        return all().stream().filter(message -> !message.isRead()).count();
    }

    /**
     * How many messages this sender has posted since {@code since}.
     *
     * <p>Equality on the hash is pushed to Firestore; the time window is applied in memory.
     * Combining an equality filter with a range filter on a different field needs a
     * composite index, which would have to be created by hand in the console before the
     * contact form worked at all — a deployment step whose only symptom is a 500 on the
     * first message a visitor sends. One indexed equality plus a filter over the handful of
     * rows it returns costs nothing and needs no console visit.
     */
    public long countRecentFrom(String senderIpHash, Instant since) {
        return Fs.documents(
                        firestore.collection(Collections.MESSAGES)
                                .whereEqualTo("senderIpHash", senderIpHash)
                                .get(),
                        "counting recent messages")
                .stream()
                .map(ContactMessageRepository::toMessage)
                .filter(message -> message.getCreatedAt() != null && message.getCreatedAt().isAfter(since))
                .count();
    }

    public ContactMessage save(ContactMessage message) {
        Instant now = Instant.now();
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(now);
        }
        message.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.MESSAGES)
                .document(message.getId().toString())
                .set(toMap(message)), "saving message " + message.getId());
        return message;
    }

    public void deleteById(UUID id) {
        Fs.block(firestore.collection(Collections.MESSAGES).document(id.toString()).delete(),
                "deleting message " + id);
    }

    private List<ContactMessage> all() {
        return Fs.documents(firestore.collection(Collections.MESSAGES).get(), "reading messages")
                .stream()
                .map(ContactMessageRepository::toMessage)
                .toList();
    }

    static ContactMessage toMessage(DocumentSnapshot doc) {
        ContactMessage message = new ContactMessage();
        message.setId(UUID.fromString(doc.getId()));
        message.setName(str(doc, "name"));
        message.setEmail(str(doc, "email"));
        message.setSubject(str(doc, "subject"));
        message.setBody(str(doc, "body"));
        message.setRead(bool(doc, "read"));
        message.setSenderIpHash(str(doc, "senderIpHash"));
        message.setCreatedAt(Documents.instant(doc, "createdAt"));
        message.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return message;
    }

    static Map<String, Object> toMap(ContactMessage m) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", m.getName());
        map.put("email", m.getEmail());
        map.put("subject", m.getSubject());
        map.put("body", m.getBody());
        map.put("read", m.isRead());
        map.put("senderIpHash", m.getSenderIpHash());
        map.put("createdAt", Documents.toField(m.getCreatedAt()));
        map.put("updatedAt", Documents.toField(m.getUpdatedAt()));
        return map;
    }
}
