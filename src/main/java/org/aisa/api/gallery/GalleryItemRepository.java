package org.aisa.api.gallery;

import static org.aisa.api.firestore.Documents.str;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
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
public class GalleryItemRepository {

    private final Firestore firestore;

    public GalleryItemRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<GalleryItem> findAllNewestFirst() {
        return all().stream()
                .sorted(newestFirst())
                .toList();
    }

    public List<GalleryItem> findByCategory(String category) {
        return findAllNewestFirst().stream()
                .filter(item -> category.equals(item.getCategory()))
                .toList();
    }

    public List<GalleryItem> findByAlbum(String albumId) {
        return all().stream()
                .filter(item -> albumId.equals(item.getAlbumId()))
                .sorted(Comparator.comparing(
                        item -> item.getAlbumIndex() == null ? 0 : item.getAlbumIndex()))
                .toList();
    }

    public Optional<GalleryItem> findById(UUID id) {
        DocumentSnapshot doc = Fs.block(
                firestore.collection(Collections.GALLERY).document(id.toString()).get(),
                "reading gallery item " + id);
        return doc.exists() ? Optional.of(toItem(doc)) : Optional.empty();
    }

    public long count() {
        return all().size();
    }

    public GalleryItem save(GalleryItem item) {
        Instant now = Instant.now();
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(now);
        }
        item.setUpdatedAt(now);
        Fs.block(firestore.collection(Collections.GALLERY)
                .document(item.getId().toString())
                .set(toMap(item)), "saving gallery item " + item.getId());
        return item;
    }

    /**
     * Writes a whole album atomically.
     *
     * <p>An album is all-or-nothing: a partial one leaves gaps in {@code albumIndex} and the
     * lightbox's next/previous walks off the end. Firestore batches cap at 500 writes, well
     * above the 50-image limit the request DTO enforces.
     */
    public List<GalleryItem> saveAll(List<GalleryItem> items) {
        WriteBatch batch = firestore.batch();
        Instant now = Instant.now();
        for (GalleryItem item : items) {
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(now);
            }
            item.setUpdatedAt(now);
            batch.set(firestore.collection(Collections.GALLERY).document(item.getId().toString()),
                    toMap(item));
        }
        Fs.block(batch.commit(), "saving " + items.size() + " gallery items");
        return items;
    }

    public void deleteById(UUID id) {
        Fs.block(firestore.collection(Collections.GALLERY).document(id.toString()).delete(),
                "deleting gallery item " + id);
    }

    /** Deletes a whole album in one batch, for the same reason creation is batched. */
    public void deleteAll(List<GalleryItem> items) {
        WriteBatch batch = firestore.batch();
        for (GalleryItem item : items) {
            batch.delete(firestore.collection(Collections.GALLERY).document(item.getId().toString()));
        }
        Fs.block(batch.commit(), "deleting " + items.size() + " gallery items");
    }

    /**
     * Newest first, tolerating a missing createdAt.
     *
     * <p>Firestore's own orderBy would silently DROP any document missing the sort field —
     * so a hand-added document would disappear from the gallery entirely rather than sort
     * oddly. Comparing in memory with a null-safe comparator cannot lose a row.
     */
    private static Comparator<GalleryItem> newestFirst() {
        return Comparator.comparing(
                GalleryItem::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<GalleryItem> all() {
        return Fs.documents(firestore.collection(Collections.GALLERY).get(), "reading gallery")
                .stream()
                .map(GalleryItemRepository::toItem)
                .toList();
    }

    static GalleryItem toItem(DocumentSnapshot doc) {
        GalleryItem item = new GalleryItem();
        item.setId(UUID.fromString(doc.getId()));
        item.setTitle(str(doc, "title"));
        item.setDescription(str(doc, "description"));
        item.setCategory(str(doc, "category"));
        item.setTakenOn(Documents.date(doc, "takenOn"));
        item.setUrl(str(doc, "url"));
        item.setPublicId(str(doc, "publicId"));
        item.setAlbumId(str(doc, "albumId"));
        item.setAlbumTitle(str(doc, "albumTitle"));
        item.setAlbumIndex(Documents.integer(doc, "albumIndex"));
        item.setAlbumTotal(Documents.integer(doc, "albumTotal"));
        item.setCreatedAt(Documents.instant(doc, "createdAt"));
        item.setUpdatedAt(Documents.instant(doc, "updatedAt"));
        return item;
    }

    static Map<String, Object> toMap(GalleryItem g) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", g.getTitle());
        map.put("description", g.getDescription());
        map.put("category", g.getCategory());
        map.put("takenOn", Documents.toField(g.getTakenOn()));
        map.put("url", g.getUrl());
        map.put("publicId", g.getPublicId());
        map.put("albumId", g.getAlbumId());
        map.put("albumTitle", g.getAlbumTitle());
        map.put("albumIndex", g.getAlbumIndex());
        map.put("albumTotal", g.getAlbumTotal());
        map.put("createdAt", Documents.toField(g.getCreatedAt()));
        map.put("updatedAt", Documents.toField(g.getUpdatedAt()));
        return map;
    }
}
