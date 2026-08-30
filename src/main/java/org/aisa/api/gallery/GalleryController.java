package org.aisa.api.gallery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.aisa.api.gallery.GalleryDtos.CreateGalleryRequest;
import org.aisa.api.gallery.GalleryDtos.GalleryItemResponse;
import org.aisa.api.gallery.GalleryDtos.UpdateGalleryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gallery")
@Tag(name = "Gallery", description = "Event photos, individually or as albums")
public class GalleryController {

    private final GalleryService service;

    public GalleryController(GalleryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List gallery items, newest first")
    public List<GalleryItemResponse> list(@RequestParam(required = false) String category) {
        return service.findAll(category);
    }

    @GetMapping("/albums/{albumId}")
    @Operation(summary = "Every photo in one album, in upload order")
    public List<GalleryItemResponse> album(@PathVariable String albumId) {
        return service.findAlbum(albumId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add one or more already-uploaded photos")
    public List<GalleryItemResponse> create(@Valid @RequestBody CreateGalleryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a photo's caption and category")
    public GalleryItemResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateGalleryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one photo and its stored image")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/albums/{albumId}")
    @Operation(summary = "Delete a whole album and every image in it")
    public ResponseEntity<Void> deleteAlbum(@PathVariable String albumId) {
        service.deleteAlbum(albumId);
        return ResponseEntity.noContent().build();
    }
}
