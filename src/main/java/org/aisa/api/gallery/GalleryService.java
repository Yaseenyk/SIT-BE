package org.aisa.api.gallery;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.gallery.GalleryDtos.CreateGalleryRequest;
import org.aisa.api.gallery.GalleryDtos.GalleryImage;
import org.aisa.api.gallery.GalleryDtos.GalleryItemResponse;
import org.aisa.api.gallery.GalleryDtos.UpdateGalleryRequest;
import org.aisa.api.media.MediaService;
import org.springframework.stereotype.Service;

@Service
public class GalleryService {

    private final GalleryItemRepository items;
    private final MediaService media;
    private final Clock clock;

    public GalleryService(GalleryItemRepository items, MediaService media, Clock clock) {
        this.items = items;
        this.media = media;
        this.clock = clock;
    }

    public List<GalleryItemResponse> findAll(String category) {
        List<GalleryItem> found = (category == null || category.isBlank() || "all".equalsIgnoreCase(category))
                ? items.findAllNewestFirst()
                : items.findByCategory(category);
        return found.stream().map(GalleryService::toResponse).toList();
    }

    public List<GalleryItemResponse> findAlbum(String albumId) {
        List<GalleryItem> found = items.findByAlbum(albumId);
        if (found.isEmpty()) {
            throw new NotFoundException("Album", albumId);
        }
        return found.stream().map(GalleryService::toResponse).toList();
    }

    /**
     * Adds a batch of already-uploaded photos.
     *
     * <p>One transaction for the batch: an album is all-or-nothing, and a partial album
     * with gaps in {@code albumIndex} would break the lightbox's next/previous.
     */
    public List<GalleryItemResponse> create(CreateGalleryRequest request) {
        List<GalleryImage> images = request.images();
        boolean isAlbum = images.size() > 1;
        String albumTitle = blankToNull(request.title()) == null ? "Album" : request.title().trim();
        // Millisecond timestamp, matching the old grp_<ts> convention so any ids already
        // shared in links keep the same shape.
        String albumId = isAlbum ? "grp_" + clock.millis() : null;

        List<GalleryItem> created = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            GalleryImage image = images.get(i);
            String title = isAlbum
                    ? "%s (%d/%d)".formatted(albumTitle, i + 1, images.size())
                    : firstNonBlank(request.title(), image.title(), "Untitled");

            GalleryItem item = new GalleryItem(title, image.url());
            item.setPublicId(blankToNull(image.publicId()));
            item.setDescription(blankToNull(request.description()));
            item.setCategory(blankToNull(request.category()));
            item.setTakenOn(request.takenOn());
            if (isAlbum) {
                item.setAlbumId(albumId);
                item.setAlbumTitle(albumTitle);
                item.setAlbumIndex(i);
                item.setAlbumTotal(images.size());
            }
            created.add(item);
        }

        return items.saveAll(created).stream().map(GalleryService::toResponse).toList();
    }

    public GalleryItemResponse update(UUID id, UpdateGalleryRequest request) {
        GalleryItem item = require(id);
        item.setTitle(request.title().trim());
        item.setDescription(blankToNull(request.description()));
        item.setCategory(blankToNull(request.category()));
        item.setTakenOn(request.takenOn());
        return toResponse(items.save(item));
    }

    public void delete(UUID id) {
        GalleryItem item = require(id);
        media.deleteQuietly(item.getPublicId());
        items.deleteById(id);
    }

    /**
     * Deletes a whole album, images included.
     *
     * <p>Exists because the alternative — the dashboard firing one DELETE per photo —
     * leaves a half-deleted album on screen if the browser is closed midway.
     */
    public void deleteAlbum(String albumId) {
        List<GalleryItem> album = items.findByAlbum(albumId);
        if (album.isEmpty()) {
            throw new NotFoundException("Album", albumId);
        }
        album.forEach(item -> media.deleteQuietly(item.getPublicId()));
        items.deleteAll(album);
    }

    private GalleryItem require(UUID id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("Gallery item", id));
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "Untitled";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static GalleryItemResponse toResponse(GalleryItem g) {
        return new GalleryItemResponse(
                g.getId(),
                g.getTitle(),
                g.getDescription(),
                g.getCategory(),
                g.getTakenOn(),
                g.getUrl(),
                g.getAlbumId(),
                g.getAlbumTitle(),
                g.getAlbumIndex(),
                g.getAlbumTotal());
    }
}
