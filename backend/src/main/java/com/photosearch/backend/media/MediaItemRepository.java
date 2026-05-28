package com.photosearch.backend.media;

import com.photosearch.backend.album.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByDriveFileId(String driveFileId);

    List<MediaItem> findByAlbumAndDeletedInDriveFalseOrderByCreatedInDriveAtDesc(Album album);

    List<MediaItem> findByAlbumAndIndexingStatus(Album album, IndexingStatus status);

    List<MediaItem> findByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
            Album album,
            Collection<IndexingStatus> statuses
    );

    List<MediaItem> findByDeletedInDriveFalseAndIndexingStatusIn(
            Collection<IndexingStatus> statuses
    );

    List<MediaItem> findByDeletedInDriveFalseAndIndexingStatus(IndexingStatus status);

    List<MediaItem> findByDriveFileIdInAndDeletedInDriveFalseAndIndexingStatus(
            Collection<String> driveFileIds,
            IndexingStatus status
    );

    @Query("""
            select m
            from MediaItem m
            join fetch m.album a
            where m.driveFileId in :driveFileIds
              and m.deletedInDrive = false
              and m.indexingStatus = :status
            """)
    List<MediaItem> findSearchableByDriveFileIds(
            @Param("driveFileIds") Collection<String> driveFileIds,
            @Param("status") IndexingStatus status
    );

    long countByAlbumAndDeletedInDriveFalse(Album album);

    long countByAlbumAndDeletedInDriveTrue(Album album);

    long countByAlbumAndDeletedInDriveFalseAndIndexingStatus(Album album, IndexingStatus status);

    long countByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
            Album album,
            Collection<IndexingStatus> statuses
    );

    boolean existsByDriveFileId(String driveFileId);
}