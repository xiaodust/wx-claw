package com.dust.wxclawbackfront.ilnk.media;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TempMediaController {

    private final TempMediaStore store;

    public TempMediaController(TempMediaStore store) {
        this.store = store;
    }

    @GetMapping("/api/media/{id}")
    public ResponseEntity<FileSystemResource> get(@PathVariable("id") String id) {
        TempMediaStore.Entry entry = store.get(id);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (entry.contentType() != null && !entry.contentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(entry.contentType());
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .body(new FileSystemResource(entry.path()));
    }
}
