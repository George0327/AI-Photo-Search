package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.ai.client.AiServiceClient;
import com.photosearch.indexing.ai.dto.AiIndexResponse;
import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.drive.GoogleDriveService;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItem;
import com.photosearch.indexing.media.MediaItemRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MediaItemIndexingService {

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final Executor indexingExecutor;

    public MediaItemIndexingService(GoogleDriveService googleDriveService,
                                    AiServiceClient aiServiceClient,
                                    MediaItemRepository mediaItemRepository,
                                    @Qualifier("indexingExecutor") Executor indexingExecutor) {
        this.googleDriveService = googleDriveService;
        this.aiServiceClient = aiServiceClient;
        this.mediaItemRepository = mediaItemRepository;
        this.indexingExecutor = indexingExecutor;
    }

    public MediaIndexingResult indexMediaItems(Album album, List<MediaItem> mediaItems) {
        String albumFolderId = album.getDriveFolderId();
        AtomicInteger indexedCount = new AtomicInteger(0);
        CopyOnWriteArrayList<DriveIndexFailure> failures = new CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures = mediaItems.stream()
                .map(item -> {
                    String fileId = item.getDriveFileId();
                    String filename = item.getFilename();
                    String mimeType = item.getMimeType();
                    return CompletableFuture.runAsync(() -> {
                        if (indexSingleMediaItem(albumFolderId, item, fileId, filename, mimeType, failures)) {
                            indexedCount.incrementAndGet();
                        }
                    }, indexingExecutor);
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return new MediaIndexingResult(indexedCount.get(), failures);
    }

    private boolean indexSingleMediaItem(String albumFolderId, MediaItem mediaItem,
                                         String fileId, String filename, String mimeType,
                                         List<DriveIndexFailure> failures) {
        try {
            if (mediaItem.isDeletedInDrive()) {
                failures.add(new DriveIndexFailure(fileId, filename,
                        "Skipped: media item is marked deleted in Drive."));
                return false;
            }
            mediaItem.setIndexingStatus(IndexingStatus.INDEXING);
            mediaItem.setIndexingError(null);
            mediaItemRepository.save(mediaItem);

            byte[] imageBytes = googleDriveService.downloadFileBytes(fileId);
            AiIndexResponse response = aiServiceClient.indexAlbumImage(
                    albumFolderId, fileId, filename, mimeType, imageBytes);

            if (response == null || !response.success()) {
                String error = response != null ? response.message() : "No response from AI service.";
                markFailed(mediaItem, fileId, filename, error, failures);
                return false;
            }

            mediaItem.setIndexingStatus(IndexingStatus.INDEXED);
            mediaItem.setFaissIndexedAt(Instant.now());
            mediaItem.setIndexingError(null);
            mediaItemRepository.save(mediaItem);
            return true;
        } catch (Exception e) {
            markFailed(mediaItem, fileId, filename, e.getMessage(), failures);
            return false;
        }
    }

    private void markFailed(MediaItem mediaItem, String fileId, String filename,
                             String error, List<DriveIndexFailure> failures) {
        mediaItem.setIndexingStatus(IndexingStatus.FAILED);
        mediaItem.setIndexingError(error);
        mediaItemRepository.save(mediaItem);
        failures.add(new DriveIndexFailure(fileId, filename, error));
    }
}
