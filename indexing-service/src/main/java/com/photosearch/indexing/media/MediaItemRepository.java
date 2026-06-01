package com.photosearch.indexing.media;

import com.photosearch.indexing.album.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByDriveFileId(String driveFileId);

    List<MediaItem> findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(Album album);

    List<MediaItem> findByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
            Album album, Collection<IndexingStatus> statuses);

    long countByAlbumAndDeletedInDriveFalse(Album album);
    long countByAlbumAndDeletedInDriveTrue(Album album);
    long countByAlbumAndDeletedInDriveFalseAndIndexingStatus(Album album, IndexingStatus status);
    long countByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(Album album, Collection<IndexingStatus> statuses);

    boolean existsByDriveFileId(String driveFileId);
}
