package com.photosearch.indexing.media;

/** Projection for grouped media counts per album / deletion flag / CLIP and face indexing status. */
public interface MediaStatusCount {
    Long getAlbumId();
    boolean getDeleted();
    IndexingStatus getStatus();
    IndexingStatus getFaceStatus();
    long getTotal();
}
