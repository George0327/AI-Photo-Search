package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.ai.client.AiServiceClient;
import com.photosearch.indexing.ai.dto.AiFaceIndexResponse;
import com.photosearch.indexing.ai.dto.AiLogoIndexResponse;
import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.album.AlbumRepository;
import com.photosearch.indexing.drive.GoogleDriveService;
import com.photosearch.indexing.media.MediaItem;
import com.photosearch.indexing.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaceLogoIndexingService {

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final AlbumRepository albumRepository;
    private final DriveAlbumSyncService driveAlbumSyncService;

    public DriveIndexResult indexFacesForAlbum(String driveAlbumId) {
        Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
        List<MediaItem> items = mediaItemRepository.findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);
        int indexed = 0;
        List<DriveIndexFailure> failures = new ArrayList<>();
        for (MediaItem item : items) {
            try {
                byte[] bytes = googleDriveService.downloadFileBytes(item.getDriveFileId());
                AiFaceIndexResponse resp = aiServiceClient.indexAlbumFaces(album.getDriveFolderId(),
                        item.getDriveFileId(), item.getFilename(), item.getMimeType(), bytes);
                if (resp != null && resp.success()) indexed++;
                else failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(),
                        resp != null ? resp.message() : "No response from AI service."));
            } catch (Exception e) {
                failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(), e.getMessage()));
            }
        }
        album.setFaceIndexedAt(Instant.now());
        albumRepository.save(album);
        return new DriveIndexResult(1, items.size(), indexed, 0, 0, indexed, failures.size(), failures);
    }

    public DriveIndexResult indexLogosForAlbum(String driveAlbumId) {
        Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
        List<MediaItem> items = mediaItemRepository.findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);
        int indexed = 0;
        List<DriveIndexFailure> failures = new ArrayList<>();
        for (MediaItem item : items) {
            try {
                byte[] bytes = googleDriveService.downloadFileBytes(item.getDriveFileId());
                AiLogoIndexResponse resp = aiServiceClient.indexAlbumLogos(album.getDriveFolderId(),
                        item.getDriveFileId(), item.getFilename(), item.getMimeType(), bytes);
                if (resp != null && resp.success()) indexed++;
                else failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(),
                        resp != null ? resp.message() : "No response from AI service."));
            } catch (Exception e) {
                failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(), e.getMessage()));
            }
        }
        album.setLogoIndexedAt(Instant.now());
        albumRepository.save(album);
        return new DriveIndexResult(1, items.size(), indexed, 0, 0, indexed, failures.size(), failures);
    }
}
