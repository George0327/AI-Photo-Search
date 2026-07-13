package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.sync.SyncRun;
import com.photosearch.indexing.sync.SyncRunRepository;
import com.photosearch.indexing.sync.SyncStatus;
import com.photosearch.indexing.sync.SyncType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SyncRunService {

    private final SyncRunRepository syncRunRepository;

    // started_by_user_id stays unset: the caller's identity lives in identity_db, not here
    public SyncRun start(SyncType type, String targetAlbumDriveId) {
        SyncRun syncRun = SyncRun.builder()
                .type(type).status(SyncStatus.RUNNING)
                .targetAlbumDriveId(targetAlbumDriveId).startedAt(Instant.now())
                .build();

        return syncRunRepository.save(syncRun);
    }

    public void setTargetAlbum(SyncRun syncRun, String driveAlbumId, String title) {
        syncRun.setTargetAlbumDriveId(driveAlbumId);
        syncRun.setTargetAlbumTitle(title);

        syncRunRepository.save(syncRun);
    }

    public void finishFromResult(SyncRun syncRun, int albumsScanned, int mediaSeen,
                                  int mediaIndexed, int mediaFailed, String partialMessage) {
        syncRun.setAlbumsScanned(albumsScanned);
        syncRun.setMediaSeen(mediaSeen);
        syncRun.setMediaIndexed(mediaIndexed);
        syncRun.setMediaFailed(mediaFailed);

        if (mediaFailed == 0) syncRun.markSuccess();
        else syncRun.markPartial(partialMessage);

        syncRunRepository.save(syncRun);
    }

    public void finishFailed(SyncRun syncRun, Exception exception) {
        syncRun.markFailed(exception.getMessage());
        syncRunRepository.save(syncRun);
    }
}
