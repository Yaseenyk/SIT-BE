package org.aisa.api.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import org.aisa.api.media.MediaService.UploadSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only. Both endpoints fall under the "anything that is not a GET requires
 * ROLE_ADMIN" rule in SecurityConfig, so an anonymous visitor cannot mint an upload
 * signature and use the association's Cloudinary quota as free image hosting.
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Signed direct-to-Cloudinary image uploads")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    public record SignatureRequest(
            @NotBlank(message = "Folder is required")
            @Pattern(regexp = "gallery|members|events|achievements|committees",
                    message = "Unknown upload folder")
            String folder) {}

    @PostMapping("/signature")
    @Operation(summary = "Mint a short-lived signature for a direct browser upload")
    public UploadSignature sign(@Valid @RequestBody SignatureRequest request) {
        return mediaService.signUpload(request.folder());
    }

    /**
     * Deleting by public id as a query parameter, not a path variable: Cloudinary public
     * ids contain slashes ({@code aisa/gallery/abc123}), which a path variable would split
     * into segments and fail to match.
     */
    @DeleteMapping
    @Operation(summary = "Delete an uploaded asset by its Cloudinary public id")
    public ResponseEntity<Void> delete(@RequestParam String publicId) {
        mediaService.deleteQuietly(publicId);
        return ResponseEntity.noContent().build();
    }
}
