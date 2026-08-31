package org.aisa.api.image;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.aisa.api.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Accepting an upload, and handing the bytes back. */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    /**
     * The ceiling, well under Firestore's 1 MiB document limit.
     *
     * <p>Base64 inflates by 4/3, so 700 KB of image is ~933 KB stored — close enough to
     * the limit that a stray byte of metadata could push a document over it. 600 KB leaves
     * real headroom, and the browser resizes to about a tenth of that before uploading, so
     * this is a backstop for a client that did not, not the normal path.
     */
    private static final int MAX_BYTES = 600 * 1024;

    /**
     * Raster formats only, and an allow-list rather than a block-list.
     *
     * <p>SVG is deliberately absent. An SVG is a document that can carry script, and this
     * endpoint serves what it is given straight back to a browser — accepting one would
     * turn an image upload into stored XSS on our own origin.
     */
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final StoredImageRepository images;

    public ImageService(StoredImageRepository images) {
        this.images = images;
    }

    /**
     * Stores an image and returns its id.
     *
     * <p>Accepts either a bare base64 payload or a full {@code data:} URI, because the
     * browser's {@code canvas.toDataURL()} produces the latter and making every call site
     * strip the prefix is a rule someone eventually forgets.
     */
    public String store(String payload, String declaredType, String folder) {
        String base64 = payload;
        String contentType = declaredType;

        if (payload.startsWith("data:")) {
            int comma = payload.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("That does not look like an image.");
            }
            String header = payload.substring(5, comma);
            if (header.contains(";")) {
                contentType = header.substring(0, header.indexOf(';'));
            }
            base64 = payload.substring(comma + 1);
        }

        contentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Only JPEG, PNG and WebP images are accepted (got "
                            + (contentType.isBlank() ? "no type" : contentType) + ").");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("The image data was not valid base64.");
        }

        if (decoded.length == 0) {
            throw new IllegalArgumentException("The image is empty.");
        }
        if (decoded.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "That image is " + (decoded.length / 1024) + " KB. The limit is "
                            + (MAX_BYTES / 1024) + " KB — a Firestore document cannot hold more. "
                            + "Try a smaller photo.");
        }
        /*
         * The declared type is not trusted. A caller can label anything image/jpeg, and
         * this endpoint hands the bytes back with that Content-Type — so the magic number
         * is what decides.
         */
        if (!looksLike(decoded, contentType)) {
            throw new IllegalArgumentException(
                    "The file's contents do not match its type. It may be renamed rather than converted.");
        }

        String id = UUID.randomUUID().toString();
        images.save(new StoredImage(id, base64, contentType, decoded.length, folder));
        log.info("Stored a {} KB {} in {}", decoded.length / 1024, contentType, folder);
        return id;
    }

    /** The decoded bytes, for the endpoint that serves them. */
    public StoredImageBytes load(String id) {
        StoredImage image = images.findById(id)
                .orElseThrow(() -> new NotFoundException("Image", id));
        return new StoredImageBytes(
                Base64.getDecoder().decode(image.getData()), image.getContentType());
    }

    /** Never throws: a failed cleanup must not fail the delete that triggered it. */
    public void deleteQuietly(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        try {
            images.deleteById(id);
        } catch (RuntimeException ex) {
            log.warn("Could not delete image {}: {}", id, ex.getMessage());
        }
    }

    public void deleteAllQuietly(List<String> ids) {
        try {
            images.deleteAll(ids.stream().filter(id -> id != null && !id.isBlank()).toList());
        } catch (RuntimeException ex) {
            log.warn("Could not delete {} image(s): {}", ids.size(), ex.getMessage());
        }
    }

    public long totalBytes() {
        return images.totalBytes();
    }

    /** Magic numbers, checked against the declared type. */
    private static boolean looksLike(byte[] b, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            case "image/png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            // "RIFF" .... "WEBP"
            case "image/webp" -> b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    public record StoredImageBytes(byte[] bytes, String contentType) {}
}
