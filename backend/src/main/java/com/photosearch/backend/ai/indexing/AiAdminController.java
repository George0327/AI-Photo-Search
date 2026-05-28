package com.photosearch.backend.ai.indexing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final DriveAiIndexingService driveAiIndexingService;

    @GetMapping("/albums/status")
    public List<AlbumIndexStatus> getAlbumStatuses() {
        return driveAiIndexingService.getAlbumIndexStatuses();
    }

    @PostMapping("/index-album/{albumId}")
    public DriveIndexResult indexAlbum(
            @PathVariable String albumId
    ) {
        return driveAiIndexingService.indexNewPhotosInAlbum(albumId);
    }

    @PostMapping("/reindex-album/{albumId}")
    public DriveIndexResult reindexAlbum(
            @PathVariable String albumId
    ) {
        return driveAiIndexingService.reindexWholeAlbum(albumId);
    }

    @PostMapping("/index-all")
    public DriveIndexResult indexAll() {
        return driveAiIndexingService.indexNewPhotosInAllAlbums();
    }
}