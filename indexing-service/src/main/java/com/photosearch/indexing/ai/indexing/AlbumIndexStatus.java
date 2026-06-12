package com.photosearch.indexing.ai.indexing;

import java.time.Instant;

public record AlbumIndexStatus(
        Long albumDatabaseId,
        String driveAlbumId,
        String title,
        String year,
        long totalActiveImages,
        long indexedImages,
        long needsIndexImages,
        long deletedImages,
        boolean reindexRecommended,
        boolean indexingImages,
        long faceIndexedImages,
        long faceNeedsIndexImages,
        boolean indexingFaces,
        Instant faceIndexedAt
) {}
