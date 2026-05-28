package com.photosearch.backend.ai.indexing;

import com.photosearch.backend.sync.SyncRun;
import com.photosearch.backend.sync.SyncRunRepository;
import com.photosearch.backend.sync.SyncStatus;
import com.photosearch.backend.sync.SyncType;
import com.photosearch.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SyncRunService {

    private final SyncRunRepository syncRunRepository;
    private final UserRepository userRepository;

    public SyncRun start(SyncType type, String targetAlbumDriveId) {
        SyncRun.SyncRunBuilder builder = SyncRun.builder()
                .type(type)
                .status(SyncStatus.RUNNING)
                .targetAlbumDriveId(targetAlbumDriveId)
                .startedAt(Instant.now());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            userRepository.findByGoogleSub(oidcUser.getSubject())
                    .ifPresent(builder::startedBy);
        }

        return syncRunRepository.save(builder.build());
    }

    public void setTargetAlbum(SyncRun syncRun, String driveAlbumId, String title) {
        syncRun.setTargetAlbumDriveId(driveAlbumId);
        syncRun.setTargetAlbumTitle(title);
        syncRunRepository.save(syncRun);
    }

    public void finishSuccess(
            SyncRun syncRun,
            int albumsScanned,
            int mediaSeen,
            int mediaIndexed,
            int mediaFailed
    ) {
        syncRun.setAlbumsScanned(albumsScanned);
        syncRun.setMediaSeen(mediaSeen);
        syncRun.setMediaIndexed(mediaIndexed);
        syncRun.setMediaFailed(mediaFailed);
        syncRun.markSuccess();
        syncRunRepository.save(syncRun);
    }

    public void finishPartial(
            SyncRun syncRun,
            int albumsScanned,
            int mediaSeen,
            int mediaIndexed,
            int mediaFailed,
            String message
    ) {
        syncRun.setAlbumsScanned(albumsScanned);
        syncRun.setMediaSeen(mediaSeen);
        syncRun.setMediaIndexed(mediaIndexed);
        syncRun.setMediaFailed(mediaFailed);
        syncRun.markPartial(message);
        syncRunRepository.save(syncRun);
    }

    public void finishFromResult(
            SyncRun syncRun,
            int albumsScanned,
            int mediaSeen,
            int mediaIndexed,
            int mediaFailed,
            String partialMessage
    ) {
        if (mediaFailed == 0) {
            finishSuccess(syncRun, albumsScanned, mediaSeen, mediaIndexed, mediaFailed);
        } else {
            finishPartial(syncRun, albumsScanned, mediaSeen, mediaIndexed, mediaFailed, partialMessage);
        }
    }

    public void finishFailed(SyncRun syncRun, Exception exception) {
        syncRun.markFailed(exception.getMessage());
        syncRunRepository.save(syncRun);
    }
}
