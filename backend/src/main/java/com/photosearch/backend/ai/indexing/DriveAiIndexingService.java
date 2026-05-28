package com.photosearch.backend.ai.indexing;

import com.photosearch.backend.ai.client.AiServiceClient;
import com.photosearch.backend.ai.dto.AiIndexResponse;
import com.photosearch.backend.album.Album;
import com.photosearch.backend.album.AlbumRepository;
import com.photosearch.backend.media.IndexingStatus;
import com.photosearch.backend.media.MediaItem;
import com.photosearch.backend.media.MediaItemRepository;
import com.photosearch.backend.sync.SyncRun;
import com.photosearch.backend.sync.SyncType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.photosearch.backend.ai.indexing.IndexingConstants.NEEDS_INDEXING_STATUSES;

@Service
@RequiredArgsConstructor
public class DriveAiIndexingService {

    private final DriveAlbumSyncService driveAlbumSyncService;
    private final MediaItemIndexingService mediaItemIndexingService;
    private final AlbumIndexStatusService albumIndexStatusService;
    private final SyncRunService syncRunService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final AlbumRepository albumRepository;

    public List<AlbumIndexStatus> getAlbumIndexStatuses() {
        List<Album> albums = albumRepository.findByEnabledTrueAndDeletedInDriveFalseOrderByTitleAsc();
        return albums.stream()
                .map(albumIndexStatusService::buildAlbumIndexStatus)
                .toList();
    }

    public DriveIndexResult indexNewPhotosInAlbum(String driveAlbumId) {
        SyncRun syncRun = syncRunService.start(SyncType.AI_INDEX_ALBUM, driveAlbumId);

        try {
            Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
            syncRunService.setTargetAlbum(syncRun, album.getDriveFolderId(), album.getTitle());

            List<MediaItem> mediaToIndex =
                    mediaItemRepository.findByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
                            album,
                            NEEDS_INDEXING_STATUSES
                    );

            MediaIndexingResult indexingResult =
                    mediaItemIndexingService.indexMediaItems(album, mediaToIndex);

            if (indexingResult.indexedCount() > 0) {
                album.setLastIndexedAt(Instant.now());
            }

            int mediaSeen = (int) mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);

            syncRunService.finishFromResult(
                    syncRun,
                    1,
                    mediaSeen,
                    indexingResult.indexedCount(),
                    indexingResult.failures().size(),
                    "Some photos failed to index."
            );

            return albumIndexStatusService.buildDriveIndexResult(
                    1,
                    List.of(album),
                    indexingResult.indexedCount(),
                    indexingResult.failures()
            );
        } catch (Exception e) {
            syncRunService.finishFailed(syncRun, e);
            throw e;
        }
    }

    public DriveIndexResult reindexWholeAlbum(String driveAlbumId) {
        SyncRun syncRun = syncRunService.start(SyncType.FAISS_REBUILD, driveAlbumId);

        try {
            Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
            syncRunService.setTargetAlbum(syncRun, album.getDriveFolderId(), album.getTitle());

            AiIndexResponse resetResponse = aiServiceClient.resetAlbumIndex(driveAlbumId);

            if (resetResponse == null || !resetResponse.success()) {
                throw new IllegalStateException(
                        resetResponse != null
                                ? resetResponse.message()
                                : "No response from AI service when resetting album index."
                );
            }

            List<MediaItem> activeMedia =
                    mediaItemRepository.findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);

            resetMediaIndexingState(activeMedia);

            MediaIndexingResult indexingResult =
                    mediaItemIndexingService.indexMediaItems(album, activeMedia);

            if (indexingResult.indexedCount() > 0) {
                album.setLastIndexedAt(Instant.now());
            }

            syncRunService.finishFromResult(
                    syncRun,
                    1,
                    activeMedia.size(),
                    indexingResult.indexedCount(),
                    indexingResult.failures().size(),
                    "Some photos failed to reindex."
            );

            return albumIndexStatusService.buildDriveIndexResult(
                    1,
                    List.of(album),
                    indexingResult.indexedCount(),
                    indexingResult.failures()
            );
        } catch (Exception e) {
            syncRunService.finishFailed(syncRun, e);
            throw e;
        }
    }

    public DriveIndexResult indexNewPhotosInAllAlbums() {
        SyncRun syncRun = syncRunService.start(SyncType.AI_INDEX_ALL, null);

        try {
            List<Album> albums = driveAlbumSyncService.syncAllDriveAlbums();

            int totalIndexedCount = 0;
            int totalMediaSeen = 0;
            List<DriveIndexFailure> allFailures = new ArrayList<>();

            for (Album album : albums) {
                List<MediaItem> mediaToIndex =
                        mediaItemRepository.findByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
                                album,
                                NEEDS_INDEXING_STATUSES
                        );

                totalMediaSeen += mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);

                MediaIndexingResult indexingResult =
                        mediaItemIndexingService.indexMediaItems(album, mediaToIndex);

                if (indexingResult.indexedCount() > 0) {
                    album.setLastIndexedAt(Instant.now());
                }

                totalIndexedCount += indexingResult.indexedCount();
                allFailures.addAll(indexingResult.failures());
            }

            syncRunService.finishFromResult(
                    syncRun,
                    albums.size(),
                    totalMediaSeen,
                    totalIndexedCount,
                    allFailures.size(),
                    "Some photos failed to index."
            );

            return albumIndexStatusService.buildDriveIndexResult(
                    albums.size(),
                    albums,
                    totalIndexedCount,
                    allFailures
            );
        } catch (Exception e) {
            syncRunService.finishFailed(syncRun, e);
            throw e;
        }
    }

    @Transactional
    private void resetMediaIndexingState(List<MediaItem> mediaItems) {
        for (MediaItem mediaItem : mediaItems) {
            mediaItem.setIndexingStatus(IndexingStatus.NOT_INDEXED);
            mediaItem.setFaissIndexedAt(null);
            mediaItem.setIndexingError(null);
        }

        mediaItemRepository.saveAll(mediaItems);
    }
}