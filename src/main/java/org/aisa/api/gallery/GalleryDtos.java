package org.aisa.api.gallery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GalleryDtos {

    private GalleryDtos() {
    }

    public record GalleryItemResponse(
            UUID id,
            String title,
            String description,
            String category,
            LocalDate takenOn,
            String url,
            String albumId,
            String albumTitle,
            Integer albumIndex,
            Integer albumTotal) {}

    /** One already-uploaded image. The upload itself went browser to Cloudinary. */
    public record GalleryImage(
            @NotBlank(message = "An image URL is required")
            String url,
            String publicId,
            @Size(max = 200) String title) {}

    /**
     * Creating gallery items is a batch operation, because that is what the admin actually
     * does: they select several photos from an event at once. A single-item endpoint
     * called in a loop would mean one HTTP round trip and one transaction per photo, and a
     * half-created album when the fifth one fails.
     */
    public record CreateGalleryRequest(
            @NotEmpty(message = "Add at least one image")
            @Size(max = 50, message = "Add at most 50 images at a time")
            List<@Valid GalleryImage> images,

            /** Used as the album title for a batch, or the item title for a single photo. */
            @Size(max = 200) String title,
            String description,
            @Size(max = 64) String category,
            LocalDate takenOn) {}

    /** Editing metadata after the fact. The image itself is not replaceable — delete and re-add. */
    public record UpdateGalleryRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200) String title,
            String description,
            @Size(max = 64) String category,
            LocalDate takenOn) {}
}
