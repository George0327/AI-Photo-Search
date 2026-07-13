package com.photosearch.indexing.media;

// projection for grouped media counts per album and status
public interface MediaStatusCount {
    Long getAlbumId();
    boolean getDeleted();
    IndexingStatus getStatus();
    IndexingStatus getFaceStatus();
    long getTotal();
}
