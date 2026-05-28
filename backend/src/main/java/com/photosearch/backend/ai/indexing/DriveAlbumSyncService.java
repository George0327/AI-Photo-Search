package com.photosearch.backend.ai.indexing;

import com.photosearch.backend.album.Album;
import com.photosearch.backend.album.AlbumRepository;
import com.photosearch.backend.drive.DriveFileInfo;
import com.photosearch.backend.drive.GoogleDriveService;
import com.photosearch.backend.media.IndexingStatus;
import com.photosearch.backend.media.MediaItem;
import com.photosearch.backend.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DriveAlbumSyncService {

    private final GoogleDriveService googleDriveService;
    private final AlbumRepository albumRepository;
    private final MediaItemRepository mediaItemRepository;

    @Transactional
    public List<Album> syncAllDriveAlbums() {
        String rootId = googleDriveService.resolveRootFolderId();
        List<DriveFileInfo> driveAlbums = googleDriveService.listAlbumFolders(rootId);

        Set<String> currentDriveAlbumIds = new HashSet<>();
        List<Album> syncedAlbums = new ArrayList<>();

        for (DriveFileInfo driveAlbum : driveAlbums) {
            currentDriveAlbumIds.add(driveAlbum.getId());
            syncedAlbums.add(syncAlbumMetadata(driveAlbum.getId()));
        }

        List<Album> activeDbAlbums = albumRepository.findByDeletedInDriveFalseOrderByTitleAsc();
        List<Album> toDisable = new ArrayList<>();

        for (Album album : activeDbAlbums) {
            if (!currentDriveAlbumIds.contains(album.getDriveFolderId())) {
                album.setDeletedInDrive(true);
                album.setEnabled(false);
                toDisable.add(album);
            }
        }

        if (!toDisable.isEmpty()) {
            albumRepository.saveAll(toDisable);
        }

        return syncedAlbums;
    }

    @Transactional
    public Album syncAlbumMetadata(String driveAlbumId) {
        DriveFileInfo albumInfo = googleDriveService.getFileInfo(driveAlbumId);

        Album album = albumRepository.findByDriveFolderId(driveAlbumId)
                .orElseGet(() -> Album.builder()
                        .driveFolderId(driveAlbumId)
                        .enabled(true)
                        .deletedInDrive(false)
                        .build()
                );

        album.setTitle(albumInfo.getName());
        album.setDeletedInDrive(false);
        album.setLastSyncedAt(Instant.now());
        album = albumRepository.save(album);

        syncMediaItemsForAlbum(album);

        return album;
    }

    private void syncMediaItemsForAlbum(Album album) {
        List<DriveFileInfo> driveImages = googleDriveService.listImagesInAlbum(album.getDriveFolderId());

        Set<String> currentDriveFileIds = new HashSet<>();
        Instant now = Instant.now();
        List<MediaItem> toSave = new ArrayList<>();

        for (DriveFileInfo driveImage : driveImages) {
            if (!isSupportedDriveImage(driveImage)) continue;

            currentDriveFileIds.add(driveImage.getId());

            MediaItem mediaItem = mediaItemRepository.findByDriveFileId(driveImage.getId())
                    .orElseGet(() -> MediaItem.builder()
                            .driveFileId(driveImage.getId())
                            .indexingStatus(IndexingStatus.NOT_INDEXED)
                            .deletedInDrive(false)
                            .build()
                    );

            boolean driveFileChanged =
                    mediaItem.getModifiedInDriveAt() != null
                            && driveImage.getModifiedTime() != null
                            && driveImage.getModifiedTime().isAfter(mediaItem.getModifiedInDriveAt());

            if (driveFileChanged && mediaItem.getIndexingStatus() == IndexingStatus.INDEXED) {
                mediaItem.setIndexingStatus(IndexingStatus.STALE);
            }

            if (mediaItem.getIndexingStatus() == IndexingStatus.DELETED) {
                mediaItem.setIndexingStatus(IndexingStatus.NOT_INDEXED);
                mediaItem.setFaissIndexedAt(null);
                mediaItem.setIndexingError(null);
            }

            mediaItem.setAlbum(album);
            mediaItem.setFilename(driveImage.getName());
            mediaItem.setMimeType(driveImage.getMimeType());
            mediaItem.setDriveThumbnailLink(driveImage.getThumbnailLink());
            mediaItem.setCreatedInDriveAt(driveImage.getCreatedTime());
            mediaItem.setModifiedInDriveAt(driveImage.getModifiedTime());
            mediaItem.setLastSeenInDriveAt(now);
            mediaItem.setDeletedInDrive(false);

            toSave.add(mediaItem);
        }

        mediaItemRepository.saveAll(toSave);

        markMissingMediaItemsAsDeleted(album, currentDriveFileIds);
    }

    private void markMissingMediaItemsAsDeleted(Album album, Set<String> currentDriveFileIds) {
        List<MediaItem> existingActiveItems =
                mediaItemRepository.findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);

        List<MediaItem> toDelete = new ArrayList<>();
        for (MediaItem existingItem : existingActiveItems) {
            if (!currentDriveFileIds.contains(existingItem.getDriveFileId())) {
                existingItem.setDeletedInDrive(true);
                existingItem.setIndexingStatus(IndexingStatus.DELETED);
                existingItem.setIndexingError("Deleted from Google Drive.");
                toDelete.add(existingItem);
            }
        }

        if (!toDelete.isEmpty()) {
            mediaItemRepository.saveAll(toDelete);
        }
    }

    private boolean isSupportedDriveImage(DriveFileInfo file) {
        return file.getMimeType() != null && file.getMimeType().startsWith("image/");
    }
}
