package com.photosearch.indexing.ai.indexing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final DriveAiIndexingService driveAiIndexingService;
    private final FaceLogoIndexingService faceLogoIndexingService;

    @GetMapping("/albums/status")
    public List<AlbumIndexStatus> getAlbumStatuses() {
        return driveAiIndexingService.getAlbumIndexStatuses();
    }

    @PostMapping("/albums/sync")
    public List<AlbumIndexStatus> syncAndGetAlbumStatuses() {
        return driveAiIndexingService.syncAndGetAlbumIndexStatuses();
    }

    @PostMapping("/index-album/{albumId}")
    public DriveIndexResult indexAlbum(@PathVariable String albumId) {
        return driveAiIndexingService.indexNewPhotosInAlbum(albumId);
    }

    @PostMapping("/reindex-album/{albumId}")
    public DriveIndexResult reindexAlbum(@PathVariable String albumId) {
        return driveAiIndexingService.reindexWholeAlbum(albumId);
    }

    @PostMapping("/index-all")
    public DriveIndexResult indexAll() {
        return driveAiIndexingService.indexNewPhotosInAllAlbums();
    }

    @PostMapping("/index-faces/{albumId}")
    public DriveIndexResult indexFaces(@PathVariable String albumId) {
        return faceLogoIndexingService.indexFacesForAlbum(albumId);
    }

}
