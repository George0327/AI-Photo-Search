package com.photosearch.indexing.ai.indexing;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final DriveAiIndexingService driveAiIndexingService;
    private final FaceIndexingService faceIndexingService;

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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OWNER')")
    public DriveIndexResult indexAll() {
        return driveAiIndexingService.indexNewPhotosInAllAlbums();
    }

    @PostMapping("/index-faces/{albumId}")
    public DriveIndexResult indexFaces(@PathVariable String albumId) {
        return faceIndexingService.indexNewFacesInAlbum(albumId);
    }

    @PostMapping("/reindex-faces/{albumId}")
    public DriveIndexResult reindexFaces(@PathVariable String albumId) {
        return faceIndexingService.reindexFacesForAlbum(albumId);
    }

    /** Cooperatively stop an in-progress CLIP (index/reindex) run for this album. */
    @PostMapping("/stop-index-album/{albumId}")
    public void stopIndexAlbum(@PathVariable String albumId) {
        driveAiIndexingService.requestStop(albumId);
    }

    /** Cooperatively stop an in-progress face (index/reindex) run for this album. */
    @PostMapping("/stop-index-faces/{albumId}")
    public void stopIndexFaces(@PathVariable String albumId) {
        faceIndexingService.requestStop(albumId);
    }

}
