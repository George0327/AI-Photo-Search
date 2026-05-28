package com.photosearch.backend.ai.indexing;

import com.photosearch.backend.ai.client.AiServiceClient;
import com.photosearch.backend.ai.dto.AiIndexResponse;
import com.photosearch.backend.album.Album;
import com.photosearch.backend.drive.GoogleDriveService;
import com.photosearch.backend.media.IndexingStatus;
import com.photosearch.backend.media.MediaItem;
import com.photosearch.backend.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaItemIndexingService {

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;

    public MediaIndexingResult indexMediaItems(Album album, List<MediaItem> mediaItems) {
        int indexedCount = 0;
        List<DriveIndexFailure> failures = new ArrayList<>();

        for (MediaItem mediaItem : mediaItems) {
            boolean indexed = indexSingleMediaItem(album, mediaItem, failures);

            if (indexed) {
                indexedCount++;
            }
        }

        return new MediaIndexingResult(indexedCount, failures);
    }

    private boolean indexSingleMediaItem(
            Album album,
            MediaItem mediaItem,
            List<DriveIndexFailure> failures
    ) {
        try {
            if (mediaItem.isDeletedInDrive()) {
                failures.add(new DriveIndexFailure(
                        mediaItem.getDriveFileId(),
                        mediaItem.getFilename(),
                        "Skipped: media item is marked deleted in Drive."
                ));
                return false;
            }

            mediaItem.setIndexingStatus(IndexingStatus.INDEXING);
            mediaItem.setIndexingError(null);
            mediaItemRepository.save(mediaItem);

            byte[] imageBytes = googleDriveService.downloadFileBytes(mediaItem.getDriveFileId());

            AiIndexResponse response = aiServiceClient.indexAlbumImage(
                    album.getDriveFolderId(),
                    mediaItem.getDriveFileId(),
                    mediaItem.getFilename(),
                    mediaItem.getMimeType(),
                    imageBytes
            );

            if (response == null || !response.success()) {
                String error = response != null
                        ? response.message()
                        : "No response from AI service.";

                markFailed(mediaItem, error);

                failures.add(new DriveIndexFailure(
                        mediaItem.getDriveFileId(),
                        mediaItem.getFilename(),
                        error
                ));

                return false;
            }

            mediaItem.setIndexingStatus(IndexingStatus.INDEXED);
            mediaItem.setFaissIndexedAt(Instant.now());
            mediaItem.setIndexingError(null);
            mediaItemRepository.save(mediaItem);

            return true;
        } catch (Exception e) {
            markFailed(mediaItem, e.getMessage());

            failures.add(new DriveIndexFailure(
                    mediaItem.getDriveFileId(),
                    mediaItem.getFilename(),
                    e.getMessage()
            ));

            return false;
        }
    }

    private void markFailed(MediaItem mediaItem, String error) {
        mediaItem.setIndexingStatus(IndexingStatus.FAILED);
        mediaItem.setIndexingError(error);
        mediaItemRepository.save(mediaItem);
    }
}