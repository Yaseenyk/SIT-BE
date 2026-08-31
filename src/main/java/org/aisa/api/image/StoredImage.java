package org.aisa.api.image;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One uploaded image, held as bytes inside Firestore.
 *
 * <h2>Why the bytes are in the database at all</h2>
 *
 * <p>Cloud Storage for Firebase has never been enabled on this project and turning it on
 * now requires a billing plan; Cloudinary needs an account this deployment does not have.
 * Firestore is what is already provisioned and paid for, so that is where images go.
 *
 * <h2>The two limits this design exists to respect</h2>
 *
 * <p><b>A Firestore document cannot exceed 1 MiB.</b> Base64 inflates bytes by about a
 * third, so the real ceiling is roughly 780 KB of image. {@code ImageService} enforces a
 * limit below that and the browser resizes before uploading, so the ceiling is reached by
 * a deliberate check with a readable message rather than by a write failing at the driver.
 *
 * <p><b>An image must never be inlined into a JSON listing.</b> The original site put
 * base64 data URIs directly on the member document, so every visitor downloaded every
 * photo in full before a single one was on screen, and nothing could be cached. Here the
 * bytes live in their OWN document, in their own collection, and no list response ever
 * carries them — {@code GET /gallery} returns metadata, and each image is fetched from
 * {@code /images/{id}} as a real JPEG the browser can cache.
 *
 * <p>That separation is also why this is not a field on {@code GalleryItem}: a 700 KB
 * field on a document read by {@code findAll} would defeat the whole arrangement the first
 * time somebody wrote a convenient query.
 */
@Getter
@Setter
@NoArgsConstructor
public class StoredImage {

    /** The document id, and the path segment the browser requests. */
    private String id;

    /** Base64, without a data-URI prefix — that prefix is a rendering concern. */
    private String data;

    /** Sent back as the Content-Type, so the browser is never asked to sniff. */
    private String contentType;

    /** The decoded size. Stored so a listing can show it without decoding anything. */
    private int bytes;

    /** What this image belongs to — "gallery", "events", "members". Diagnostics only. */
    private String folder;

    private Instant createdAt;

    public StoredImage(String id, String data, String contentType, int bytes, String folder) {
        this.id = id;
        this.data = data;
        this.contentType = contentType;
        this.bytes = bytes;
        this.folder = folder;
    }
}
