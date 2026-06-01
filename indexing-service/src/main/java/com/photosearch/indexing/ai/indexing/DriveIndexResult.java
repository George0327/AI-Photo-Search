package com.photosearch.indexing.ai.indexing;

import java.util.List;

public record DriveIndexResult(
        int albumCount,
        long totalActiveImages,
        long indexedImages,
        long needsIndexImages,
        long deletedImages,
        int newlyIndexedCount,
        int failedCount,
        List<DriveIndexFailure> failures
) {}
