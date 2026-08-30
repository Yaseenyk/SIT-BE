package org.aisa.api.gallery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "gallery_item")
@Getter
@Setter
@NoArgsConstructor
public class GalleryItem extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column
    private String description;

    @Column(length = 64)
    private String category;

    @Column(name = "taken_on")
    private LocalDate takenOn;

    /** The Cloudinary delivery URL. */
    @Column(nullable = false)
    private String url;

    /**
     * The Cloudinary public id, kept so the asset can be deleted with the row.
     * Null for items added by pasting an external URL, which this API does not own.
     */
    @Column(name = "public_id", length = 255)
    private String publicId;

    @Column(name = "album_id", length = 64)
    private String albumId;

    @Column(name = "album_title", length = 200)
    private String albumTitle;

    @Column(name = "album_index")
    private Integer albumIndex;

    @Column(name = "album_total")
    private Integer albumTotal;

    public GalleryItem(String title, String url) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.url = url;
    }
}
