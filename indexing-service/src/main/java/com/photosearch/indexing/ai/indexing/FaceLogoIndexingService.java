package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.ai.client.AiServiceClient;
import com.photosearch.indexing.ai.dto.AiFaceIndexResponse;
import com.photosearch.indexing.ai.dto.AiLogoIndexResponse;
import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.album.AlbumRepository;
import com.photosearch.indexing.drive.GoogleDriveService;
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
public class FaceLogoIndexingService {

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final AlbumRepository albumRepository;
    private final DriveAlbumSyncService driveAlbumSyncService;
    private final Executor indexingExecutor;

    public FaceLogoIndexingService(GoogleDriveService googleDriveService,
                                   AiServiceClient aiServiceClient,
                                   MediaItemRepository mediaItemRepository,
                                   AlbumRepository albumRepository,
                                   DriveAlbumSyncService driveAlbumSyncService,
                                   @Qualifier("indexingExecutor") Executor indexingExecutor) {
        this.googleDriveService = googleDriveService;
        this.aiServiceClient = aiServiceClient;
        this.mediaItemRepository = mediaItemRepository;
        this.albumRepository = albumRepository;
        this.driveAlbumSyncService = driveAlbumSyncService;
        this.indexingExecutor = indexingExecutor;
    }

    public DriveIndexResult indexFacesForAlbum(String driveAlbumId) {
        Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
        String albumFolderId = album.getDriveFolderId();
        List<MediaItem> items = mediaItemRepository.findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);

        AtomicInteger indexed = new AtomicInteger(0);
        CopyOnWriteArrayList<DriveIndexFailure> failures = new CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> {
                    String fileId = item.getDriveFileId();
                    String filename = item.getFilename();
                    String mimeType = item.getMimeType();
                    return CompletableFuture.runAsync(() -> {
                        try {
                            byte[] bytes = googleDriveService.downloadFileBytes(fileId);
                            AiFaceIndexResponse resp = aiServiceClient.indexAlbumFaces(
                                    albumFolderId, fileId, filename, mimeType, bytes);
                            if (resp != null && resp.success()) indexed.incrementAndGet();
                            else failures.add(new DriveIndexFailure(fileId, filename,
                                    resp != null ? resp.message() : "No response from AI service."));
                        } catch (Exception e) {
                            failures.add(new DriveIndexFailure(fileId, filename, e.getMessage()));
                        }
                    }, indexingExecutor);
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        album.setFaceIndexedAt(Instant.now());
        albumRepository.save(album);
        int count = indexed.get();
        return new DriveIndexResult(1, items.size(), count, 0, 0, count, failures.size(), failures);
    }

    public DriveIndexResult indexLogosForAlbum(String driveAlbumId) {
        Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
        String albumFolderId = album.getDriveFolderId();
        List<MediaItem> items = mediaItemRepository.findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);

        AtomicInteger indexed = new AtomicInteger(0);
        CopyOnWriteArrayList<DriveIndexFailure> failures = new CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> {
                    String fileId = item.getDriveFileId();
                    String filename = item.getFilename();
                    String mimeType = item.getMimeType();
                    return CompletableFuture.runAsync(() -> {
                        try {
                            byte[] bytes = googleDriveService.downloadFileBytes(fileId);
                            AiLogoIndexResponse resp = aiServiceClient.indexAlbumLogos(
                                    albumFolderId, fileId, filename, mimeType, bytes);
                            if (resp != null && resp.success()) indexed.incrementAndGet();
                            else failures.add(new DriveIndexFailure(fileId, filename,
                                    resp != null ? resp.message() : "No response from AI service."));
                        } catch (Exception e) {
                            failures.add(new DriveIndexFailure(fileId, filename, e.getMessage()));
                        }
                    }, indexingExecutor);
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        album.setLogoIndexedAt(Instant.now());
        albumRepository.save(album);
        int count = indexed.get();
        return new DriveIndexResult(1, items.size(), count, 0, 0, count, failures.size(), failures);
    }
}
