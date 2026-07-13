package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.ai.client.AiServiceClient;
import com.photosearch.indexing.ai.dto.AiBatchIndexItemResult;
import com.photosearch.indexing.ai.dto.AiBatchIndexResponse;
import com.photosearch.indexing.ai.dto.AiImagePayload;
import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.drive.GoogleDriveService;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItem;
import com.photosearch.indexing.media.MediaItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

@Service
public class MediaItemIndexingService {

    private static final Logger log = LoggerFactory.getLogger(MediaItemIndexingService.class);

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final Executor indexingExecutor;
    private final Executor downloadExecutor;
    private final IndexingCancellationRegistry cancellationRegistry;
    private final int batchSize;

    public MediaItemIndexingService(GoogleDriveService googleDriveService,
                                    AiServiceClient aiServiceClient,
                                    MediaItemRepository mediaItemRepository,
                                    @Qualifier("indexingExecutor") Executor indexingExecutor,
                                    @Qualifier("downloadExecutor") Executor downloadExecutor,
                                    IndexingCancellationRegistry cancellationRegistry,
                                    @Value("${app.indexing.batch-size:8}") int batchSize) {
        this.googleDriveService = googleDriveService;
        this.aiServiceClient = aiServiceClient;
        this.mediaItemRepository = mediaItemRepository;
        this.indexingExecutor = indexingExecutor;
        this.downloadExecutor = downloadExecutor;
        this.cancellationRegistry = cancellationRegistry;
        this.batchSize = Math.max(1, batchSize);
    }

    // splits the work into chunks and runs them in parallel on the indexing pool
    public MediaIndexingResult indexMediaItems(Album album, List<MediaItem> mediaItems) {
        String albumFolderId = album.getDriveFolderId();
        AtomicInteger indexedCount = new AtomicInteger(0);
        CopyOnWriteArrayList<DriveIndexFailure> failures = new CopyOnWriteArrayList<>();

        List<MediaItem> indexable = new ArrayList<>();

        for (MediaItem item : mediaItems) {
            if (item.isDeletedInDrive()) {
                failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(),
                        "Skipped: media item is marked deleted in Drive."));
            } else {
                indexable.add(item);
            }
        }

        cancellationRegistry.begin(IndexingCancellationRegistry.Kind.CLIP, albumFolderId);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int start = 0; start < indexable.size(); start += batchSize) {
                List<MediaItem> chunk = indexable.subList(start, Math.min(start + batchSize, indexable.size()));

                futures.add(CompletableFuture.runAsync(
                        () -> indexChunk(albumFolderId, chunk, indexedCount, failures), indexingExecutor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            cancellationRegistry.end(IndexingCancellationRegistry.Kind.CLIP, albumFolderId);
        }

        return new MediaIndexingResult(indexedCount.get(), failures);
    }

    // downloads a chunk from Drive in parallel, then sends it to the AI service as one batch
    private void indexChunk(String albumFolderId, List<MediaItem> chunk,
                            AtomicInteger indexedCount, List<DriveIndexFailure> failures) {
        // bail before touching status, so queued items stay NOT_INDEXED and the run can resume
        if (cancellationRegistry.isCancelled(IndexingCancellationRegistry.Kind.CLIP, albumFolderId)) {
            return;
        }

        for (MediaItem item : chunk) {
            item.setIndexingStatus(IndexingStatus.INDEXING);
            item.setIndexingError(null);
        }

        mediaItemRepository.saveAll(chunk);

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
                byte[] imageBytes = download.getValue().join();

                payloads.add(new AiImagePayload(item.getDriveFileId(), item.getFilename(),
                        item.getMimeType(), imageBytes));
                uploaded.add(item);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                log.warn("Download failed [{}]: {}", item.getFilename(), cause.getMessage());
                markFailed(item, "Drive download failed: " + cause.getMessage(), failures);
            }
        }

        if (uploaded.isEmpty()) return;

        AiBatchIndexResponse response;

        try {
            log.info("Indexing batch of {} image(s)", uploaded.size());
            response = aiServiceClient.indexAlbumImageBatch(albumFolderId, payloads);
        } catch (Exception e) {
            log.warn("AI batch indexing failed: {}", e.getMessage());

            for (MediaItem item : uploaded) markFailed(item, e.getMessage(), failures);

            return;
        }

        Map<String, AiBatchIndexItemResult> resultsByImageId = new HashMap<>();

        if (response != null && response.results() != null) {
            for (AiBatchIndexItemResult result : response.results()) {
                resultsByImageId.put(result.imageId(), result);
            }
        }

        for (MediaItem item : uploaded) {
            AiBatchIndexItemResult result = resultsByImageId.get(item.getDriveFileId());

            if (result != null && result.success()) {
                item.setIndexingStatus(IndexingStatus.INDEXED);
                item.setFaissIndexedAt(Instant.now());
                item.setIndexingError(null);
                indexedCount.incrementAndGet();
            } else {
                String error = result != null ? result.message() : "No result from AI service for this image.";

                item.setIndexingStatus(IndexingStatus.FAILED);
                item.setIndexingError(error);
                failures.add(new DriveIndexFailure(item.getDriveFileId(), item.getFilename(), error));
            }
        }

        mediaItemRepository.saveAll(uploaded);
    }

    private void markFailed(MediaItem mediaItem, String error, List<DriveIndexFailure> failures) {
        mediaItem.setIndexingStatus(IndexingStatus.FAILED);
        mediaItem.setIndexingError(error);
        mediaItemRepository.save(mediaItem);
        failures.add(new DriveIndexFailure(mediaItem.getDriveFileId(), mediaItem.getFilename(), error));
    }
}
