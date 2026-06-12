package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.album.AlbumRepository;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indexing runs inside HTTP requests, so a crash or restart (e.g. an OOM kill)
 * strands items in INDEXING status and albums with the indexing-faces flag set.
 * Those would otherwise show as "Indexing…" forever and block new runs with a
 * 409 conflict. On startup, mark them FAILED so they can be retried incrementally.
 */
@Component
@RequiredArgsConstructor
public class IndexingCrashRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexingCrashRecovery.class);
    private static final String INTERRUPTED_ERROR = "Interrupted by a service restart. Retry indexing.";

    private final MediaItemRepository mediaItemRepository;
    private final AlbumRepository albumRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int clip = mediaItemRepository.failStuckClipIndexing(
                IndexingStatus.INDEXING, IndexingStatus.FAILED, INTERRUPTED_ERROR);
        int faces = mediaItemRepository.failStuckFaceIndexing(
                IndexingStatus.INDEXING, IndexingStatus.FAILED, INTERRUPTED_ERROR);
        int flags = albumRepository.clearStuckFaceIndexingFlags();
        if (clip > 0 || faces > 0 || flags > 0) {
            log.warn("Recovered interrupted indexing state: {} CLIP item(s), {} face item(s), {} album flag(s)",
                    clip, faces, flags);
        }
    }
}
