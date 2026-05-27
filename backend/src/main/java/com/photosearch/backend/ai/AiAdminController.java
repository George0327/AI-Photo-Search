package com.photosearch.backend.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final DriveAiIndexingService driveAiIndexingService;

    @PostMapping("/rebuild-album/{albumId}")
    public DriveAiIndexingService.DriveIndexResult rebuildAlbumIndex(
            @PathVariable String albumId
    ) {
        return driveAiIndexingService.rebuildAlbumIndex(albumId);
    }

    @PostMapping("/rebuild-drive")
    public DriveAiIndexingService.DriveIndexResult rebuildFullDriveIndex() {
        return driveAiIndexingService.rebuildFullDriveIndex();
    }
}