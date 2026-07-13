package com.photosearch.indexing.ai.indexing;

// per-album indexing summary, so the gallery can show which albums are searchable
public record AlbumSearchStatus(
        String driveAlbumId,
        long totalActiveImages,
        long clipIndexedImages,
        long clipPendingImages,
        long faceIndexedImages,
        long facePendingImages
) {
    public static AlbumSearchStatus from(AlbumIndexStatus status) {
        return new AlbumSearchStatus(
                status.driveAlbumId(),
                status.totalActiveImages(),
                status.indexedImages(),
                status.needsIndexImages(),
                status.faceIndexedImages(),
                status.faceNeedsIndexImages());
    }
}
