package com.photosearch.backend.ai.indexing;

public record DriveIndexFailure(
        String fileId,
        String fileName,
        String error
) {
}