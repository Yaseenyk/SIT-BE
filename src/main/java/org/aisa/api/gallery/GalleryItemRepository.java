package org.aisa.api.gallery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryItemRepository extends JpaRepository<GalleryItem, UUID> {

    List<GalleryItem> findAllByOrderByCreatedAtDesc();

    List<GalleryItem> findByCategoryOrderByCreatedAtDesc(String category);

    /** Every photo in one album, in the order they were uploaded. */
    List<GalleryItem> findByAlbumIdOrderByAlbumIndexAsc(String albumId);
}
