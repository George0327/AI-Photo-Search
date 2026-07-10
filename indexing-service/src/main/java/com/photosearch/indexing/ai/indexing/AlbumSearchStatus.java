package com.photosearch.indexing.ai.indexing;

/** Per-album indexing summary for regular users, so the gallery shows which albums are searchable by text and by face. */
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
