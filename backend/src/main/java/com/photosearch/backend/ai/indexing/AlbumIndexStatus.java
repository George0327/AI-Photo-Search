package com.photosearch.backend.ai.indexing;

import java.time.Instant;

public record AlbumIndexStatus(
        Long albumDatabaseId,
        String driveAlbumId,
        String title,
        long totalActiveImages,
        long indexedImages,
        long needsIndexImages,
        long deletedImages,
        boolean reindexRecommended,
        Instant faceIndexedAt,
        Instant logoIndexedAt
) {}
