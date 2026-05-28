package com.photosearch.backend.ai.indexing;

import com.photosearch.backend.media.IndexingStatus;

import java.util.List;

public final class IndexingConstants {

    private IndexingConstants() {
    }

    public static final List<IndexingStatus> NEEDS_INDEXING_STATUSES = List.of(
            IndexingStatus.NOT_INDEXED,
            IndexingStatus.FAILED,
            IndexingStatus.STALE
    );
}