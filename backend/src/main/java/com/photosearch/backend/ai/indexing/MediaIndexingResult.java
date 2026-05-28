package com.photosearch.backend.ai.indexing;

import java.util.List;

public record MediaIndexingResult(
        int indexedCount,
        List<DriveIndexFailure> failures
) {
}