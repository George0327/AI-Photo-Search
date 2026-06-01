package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.media.IndexingStatus;

import java.util.List;

public class IndexingConstants {
    public static final List<IndexingStatus> NEEDS_INDEXING_STATUSES =
            List.of(IndexingStatus.NOT_INDEXED, IndexingStatus.FAILED, IndexingStatus.STALE);
}
