package com.photosearch.backend.ai.indexing;

public record AlbumIndexStatus(
        Long albumDatabaseId,
        String driveAlbumId,
        String title,
        long totalActiveImages,
        long indexedImages,
        long needsIndexImages,
        long deletedImages,
        boolean reindexRecommended
) {
}