package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.ai.client.AiServiceClient;
import com.photosearch.indexing.ai.dto.AiFaceBatchIndexItemResult;
import com.photosearch.indexing.ai.dto.AiFaceBatchIndexResponse;
import com.photosearch.indexing.ai.dto.AiImagePayload;
import com.photosearch.indexing.ai.dto.AiIndexResponse;
import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.album.AlbumRepository;
import com.photosearch.indexing.drive.GoogleDriveService;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItem;
import com.photosearch.indexing.media.MediaItemRepository;
import com.photosearch.indexing.sync.SyncRun;
import com.photosearch.indexing.sync.SyncType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.photosearch.indexing.ai.indexing.IndexingConstants.NEEDS_INDEXING_STATUSES;

/**
 * Face indexing mirrors CLIP indexing: per-item status lifecycle
 * (NOT_INDEXED → INDEXING → INDEXED/FAILED, plus STALE/DELETED from sync),
 * incremental "index new" and a full reindex that resets the AI face index.
 */
@Service
public class FaceIndexingService {

    private static final Logger log = LoggerFactory.getLogger(FaceIndexingService.class);

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final AlbumRepository albumRepository;
    private final DriveAlbumSyncService driveAlbumSyncService;
    private final AlbumIndexStatusService albumIndexStatusService;
    private final SyncRunService syncRunService;
    private final Executor indexingExecutor;
    private final Executor downloadExecutor;
    private final int batchSize;

    public FaceIndexingService(GoogleDriveService googleDriveService,
                               AiServiceClient aiServiceClient,
                               MediaItemRepository mediaItemRepository,
                               AlbumRepository albumRepository,
                               DriveAlbumSyncService driveAlbumSyncService,
                               AlbumIndexStatusService albumIndexStatusService,
                               SyncRunService syncRunService,
                               @Qualifier("indexingExecutor") Executor indexingExecutor,
                               @Qualifier("downloadExecutor") Executor downloadExecutor,
                               @Value("${app.indexing.batch-size:8}") int batchSize) {
        this.googleDriveService = googleDriveService;
        this.aiServiceClient = aiServiceClient;
        this.mediaItemRepository = mediaItemRepository;
        this.albumRepository = albumRepository;
        this.driveAlbumSyncService = driveAlbumSyncService;
        this.albumIndexStatusService = albumIndexStatusService;
        this.syncRunService = syncRunService;
        this.indexingExecutor = indexingExecutor;
        this.downloadExecutor = downloadExecutor;
        this.batchSize = Math.max(1, batchSize);
    }

    public DriveIndexResult indexNewFacesInAlbum(String driveAlbumId) {
        SyncRun syncRun = syncRunService.start(SyncType.FACE_INDEX_ALBUM, driveAlbumId);
        try {
            Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
            syncRunService.setTargetAlbum(syncRun, album.getDriveFolderId(), album.getTitle());
            ensureNotAlreadyIndexingFaces(album);

            List<MediaItem> toIndex = mediaItemRepository
                    .findByAlbumAndDeletedInDriveFalseAndFaceIndexingStatusIn(album, NEEDS_INDEXING_STATUSES);
            return runFaceIndexing(syncRun, album, toIndex);
        } catch (Exception e) {
            syncRunService.finishFailed(syncRun, e);
            throw e;
        }
    }

    public DriveIndexResult reindexFacesForAlbum(String driveAlbumId) {
        SyncRun syncRun = syncRunService.start(SyncType.FACE_REBUILD, driveAlbumId);
        try {
            Album album = driveAlbumSyncService.syncAlbumMetadata(driveAlbumId);
            syncRunService.setTargetAlbum(syncRun, album.getDriveFolderId(), album.getTitle());
            ensureNotAlreadyIndexingFaces(album);

            AiIndexResponse resetResponse = aiServiceClient.resetAlbumFaceIndex(driveAlbumId);
            if (resetResponse == null || !resetResponse.success()) {
                throw new IllegalStateException(resetResponse != null
                        ? resetResponse.message() : "No response from AI service when resetting face index.");
            }

            List<MediaItem> activeMedia = mediaItemRepository
                    .findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(album);
            resetFaceIndexingState(activeMedia);
            return runFaceIndexing(syncRun, album, activeMedia);
        } catch (Exception e) {
            syncRunService.finishFailed(syncRun, e);
            throw e;
        }
    }

    private void ensureNotAlreadyIndexingFaces(Album album) {
        if (album.isIndexingFaces()
                || mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndFaceIndexingStatus(
                        album, IndexingStatus.INDEXING) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face indexing is already in progress for this album.");
        }
    }

    private DriveIndexResult runFaceIndexing(SyncRun syncRun, Album album, List<MediaItem> mediaItems) {
        album.setIndexingFaces(true);
        albumRepository.save(album);

        String albumFolderId = album.getDriveFolderId();
        AtomicInteger indexed = new AtomicInteger(0);
        CopyOnWriteArrayList<DriveIndexFailure> failures = new CopyOnWriteArrayList<>();

        try {
            // Same batching strategy as CLIP indexing: chunks in parallel on the
            // indexing pool, one AI request and one index save per chunk.
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int start = 0; start < mediaItems.size(); start += batchSize) {
                List<MediaItem> chunk = mediaItems.subList(start, Math.min(start + batchSize, mediaItems.size()));
                futures.add(CompletableFuture.runAsync(
                        () -> indexFaceChunk(albumFolderId, chunk, indexed, failures), indexingExecutor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            if (indexed.get() > 0) album.setFaceIndexedAt(Instant.now());
        } finally {
            album.setIndexingFaces(false);
            albumRepository.save(album);
        }

        int mediaSeen = (int) mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);
        syncRunService.finishFromResult(syncRun, 1, mediaSeen, indexed.get(),
                failures.size(), "Some photos failed face indexing.");
        return albumIndexStatusService.buildDriveIndexResult(1, List.of(album), indexed.get(), failures);
    }

    private void indexFaceChunk(String albumFolderId, List<MediaItem> chunk,
                                AtomicInteger indexed, List<DriveIndexFailure> failures) {
        for (MediaItem item : chunk) {
            item.setFaceIndexingStatus(IndexingStatus.INDEXING);
            item.setFaceIndexingError(null);
        }
        mediaItemRepository.saveAll(chunk);

        // Download the whole chunk in parallel — Drive latency, not the AI
        // service, dominates otherwise.
        Map<MediaItem, CompletableFuture<byte[]>> downloads = new LinkedHashMap<>();
        for (MediaItem item : chunk) {
            downloads.put(item, CompletableFuture.supplyAsync(
                    () -> googleDriveService.downloadFileBytes(item.getDriveFileId()), downloadExecutor));
        }

        List<AiImagePayload> payloads = new ArrayList<>();
        List<MediaItem> uploaded = new ArrayList<>();
        for (Map.Entry<MediaItem, CompletableFuture<byte[]>> download : downloads.entrySet()) {
            MediaItem item = download.getKey();
            try {
                byte[] bytes = download.getValue().join();
                payloads.add(new AiImagePayload(item.getDriveFileId(), item.getFilename(),
                        item.getMimeType(), bytes));
                uploaded.add(item);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("Download failed [{}]: {}", item.getFilename(), cause.getMessage());
                markFaceFailed(item, "Drive download failed: " + cause.getMessage(), failures);
            }
        }
        if (uploaded.isEmpty()) return;

        AiFaceBatchIndexResponse response;
        try {
            log.info("Face-indexing batch of {} image(s)", uploaded.size());
            response = aiServiceClient.indexAlbumFacesBatch(albumFolderId, payloads);
        } catch (Exception e) {
            log.warn("AI face batch indexing failed: {}", e.getMessage());
            for (MediaItem item : uploaded) markFaceFailed(item, e.getMessage(), failures);
            return;
        }

        Map<String, AiFaceBatchIndexItemResult> resultsByImageId = new HashMap<>();
        if (response != null && response.results() != null) {
            for (AiFaceBatchIndexItemResult result : response.results()) {
                resultsByImageId.put(result.imageId(), result);
            }
        }

        for (MediaItem item : uploaded) {
            AiFaceBatchIndexItemResult result = resultsByImageId.get(item.getDriveFileId());
            if (result != null && result.success()) {
                item.setFaceIndexingStatus(IndexingStatus.INDEXED);
                item.setFaceIndexedAt(Instant.now());
                item.setFaceIndexingError(null);
                indexed.incrementAndGet();
            } else {
                String error = result != null ? result.message() : "No result from AI service for this image.";
                item.setFaceIndexingStatus(IndexingStatus.FAILED);
                item.setFaceIndexingError(error);
                failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(), error));
            }
        }
        mediaItemRepository.saveAll(uploaded);
    }

    private void markFaceFailed(MediaItem item, String error, List<DriveIndexFailure> failures) {
        item.setFaceIndexingStatus(IndexingStatus.FAILED);
        item.setFaceIndexingError(error);
        mediaItemRepository.save(item);
        failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(), error));
    }

    private void resetFaceIndexingState(List<MediaItem> mediaItems) {
        for (MediaItem item : mediaItems) {
            item.setFaceIndexingStatus(IndexingStatus.NOT_INDEXED);
            item.setFaceIndexedAt(null);
            item.setFaceIndexingError(null);
        }
        mediaItemRepository.saveAll(mediaItems);
    }
}
