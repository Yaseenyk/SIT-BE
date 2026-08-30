package org.aisa.api.gallery;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * One photo in the gallery.
 *
 * <p>Photos uploaded together form an album: they share an {@code albumId} and render as a
 * single folder tile that opens into a lightbox. That grouping is carried on the item
 * rather than modelled as a separate Album table because an album has no properties of its
 * own — it is a title and a set of photos, and a table for it would be a join for nothing.
 */
@Getter
@Setter
@NoArgsConstructor
public class GalleryItem extends BaseEntity {

    private UUID id;

    private String title;

    private String description;

    private String category;

    private LocalDate takenOn;

    /** The Cloudinary delivery URL. */
    private String url;

    /**
     * The Cloudinary public id, kept so the asset can be deleted with the row.
     * Null for items added by pasting an external URL, which this API does not own.
     */
    private String publicId;

    private String albumId;

    private String albumTitle;

    private Integer albumIndex;

    private Integer albumTotal;

    public GalleryItem(String title, String url) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.url = url;
    }
}
