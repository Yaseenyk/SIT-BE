package org.aisa.api.image;

import static org.aisa.api.firestore.Documents.intOr;
import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aisa.api.firestore.Collections;
import org.aisa.api.firestore.Documents;
import org.aisa.api.firestore.Fs;
import org.springframework.stereotype.Repository;

/** Image bytes, one document each. */
@Repository
public class StoredImageRepository {

    private final Firestore firestore;

    public StoredImageRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<StoredImage> findById(String id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.IMAGES).document(id).get(), "reading image " + id);
        return doc.exists() ? Optional.of(toImage(doc)) : Optional.empty();
    }

    public StoredImage save(StoredImage image) {
        if (image.getCreatedAt() == null) {
            image.setCreatedAt(Instant.now());
        }
        Fs.block(firestore.collection(Collections.IMAGES).document(image.getId()).set(toMap(image)),
                "saving image " + image.getId());
        return image;
    }

    public void deleteById(String id) {
        Fs.block(firestore.collection(Collections.IMAGES).document(id).delete(),
                "deleting image " + id);
    }

    /**
     * Total bytes held, for the dashboard.
     *
     * <p>Reads only the {@code bytes} field of each document rather than the documents
     * themselves — a plain {@code get()} here would pull every image in the project into
     * memory to add up numbers already stored beside them.
     */
    public long totalBytes() {
        List<QueryDocumentSnapshot> docs = Fs.documents(
                firestore.collection(Collections.IMAGES).select("bytes").get(),
                "measuring stored images");
        return docs.stream().mapToLong(d -> {
            Long value = d.getLong("bytes");
            return value == null ? 0L : value;
        }).sum();
    }

    public int count() {
        return Fs.documents(firestore.collection(Collections.IMAGES).select("bytes").get(),
                "counting stored images").size();
    }

    /** Removes several at once — used when an album or an event is deleted. */
    public void deleteAll(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        WriteBatch batch = firestore.batch();
        for (String id : ids) {
            batch.delete(firestore.collection(Collections.IMAGES).document(id));
        }
        Fs.block(batch.commit(), "deleting " + ids.size() + " image(s)");
    }

    static StoredImage toImage(DocumentSnapshot doc) {
        StoredImage image = new StoredImage();
        image.setId(doc.getId());
        image.setData(str(doc, "data"));
        image.setContentType(str(doc, "contentType"));
        image.setBytes(intOr(doc, "bytes", 0));
        image.setFolder(str(doc, "folder"));
        image.setCreatedAt(Documents.instant(doc, "createdAt"));
        return image;
    }

    static Map<String, Object> toMap(StoredImage image) {
        Map<String, Object> map = new HashMap<>();
        map.put("data", image.getData());
        map.put("contentType", image.getContentType());
        map.put("bytes", image.getBytes());
        map.put("folder", image.getFolder());
        map.put("createdAt", Documents.toField(image.getCreatedAt()));
        return map;
    }
}
