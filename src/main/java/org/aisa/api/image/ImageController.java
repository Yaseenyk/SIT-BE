package org.aisa.api.image;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uploading an image, and serving it.
 *
 * <h2>Why this serves bytes rather than returning base64</h2>
 *
 * <p>The obvious version of "images in Firestore" puts a data URI in the JSON, which is
 * what the original site did: every visitor downloaded every photo in full before one was
 * on screen, the payload grew by a third from base64, and nothing could ever be cached
 * because the bytes only existed inside an API response.
 *
 * <p>So the listing endpoints return an id, and this returns a real JPEG with a real
 * Content-Type and a year-long cache header. The browser treats it exactly like any other
 * image: it requests them in parallel, only for what is on screen, and never asks twice.
 * The bytes still live in Firestore — that part is unchanged — but nothing else has to
 * know that.
 */
@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Images")
public class ImageController {

    private final ImageService images;

    public ImageController(ImageService images) {
        this.images = images;
    }

    /**
     * The bytes. Public, because everything that references an image is public.
     *
     * <p>Immutable and cached for a year: an id is minted per upload and never reused, so
     * a changed picture is a different URL. That is what makes serving images out of a
     * database affordable — the second visitor does not pay for it.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Serve a stored image")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        ImageService.StoredImageBytes image = images.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image.bytes());
    }

    /**
     * Accepts an upload. Admin-only, by the catch-all rule in SecurityConfig.
     *
     * <p>JSON rather than multipart because the browser resizes on a canvas before sending
     * — {@code toDataURL()} hands back a data URI, and a multipart round trip would mean
     * converting it back into a Blob for no benefit.
     */
    @PostMapping
    @Operation(summary = "Store an image and return its id")
    public UploadResponse upload(@Valid @RequestBody UploadRequest request) {
        String id = images.store(request.data(), request.contentType(), request.folder());
        return new UploadResponse(id, "/api/v1/images/" + id);
    }

    public record UploadRequest(
            /** Base64, or a full data: URI — both are accepted. */
            @NotBlank(message = "No image data was sent") String data,
            String contentType,
            String folder) {}

    public record UploadResponse(String id, String path) {}
}
