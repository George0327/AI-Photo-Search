package com.photosearch.indexing.media;

import com.photosearch.indexing.album.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    List<MediaItem> findByAlbumAndDeletedInDriveFalseAndFaceIndexingStatusIn(
            Album album, Collection<IndexingStatus> statuses);

    long countByAlbumAndDeletedInDriveFalse(Album album);
    long countByAlbumAndDeletedInDriveFalseAndIndexingStatus(Album album, IndexingStatus status);
    long countByAlbumAndDeletedInDriveFalseAndFaceIndexingStatus(Album album, IndexingStatus status);

    /** One grouped query over all albums, replacing the previous per-album count queries for the status dashboards. */
    @Query("""
            select m.album.id as albumId, m.deletedInDrive as deleted,
                   m.indexingStatus as status, m.faceIndexingStatus as faceStatus,
                   count(m) as total
            from MediaItem m
            where m.album in :albums
            group by m.album.id, m.deletedInDrive, m.indexingStatus, m.faceIndexingStatus
            """)
    List<MediaStatusCount> countGroupedByAlbums(@Param("albums") Collection<Album> albums);

    boolean existsByDriveFileId(String driveFileId);

    /** Recovers items left mid-flight by a crash/restart so they can be retried. */
    @Modifying
    @Query("""
            update MediaItem m
            set m.indexingStatus = :failed, m.indexingError = :error
            where m.indexingStatus = :indexing
            """)
    int failStuckClipIndexing(@Param("indexing") IndexingStatus indexing,
                              @Param("failed") IndexingStatus failed,
                              @Param("error") String error);

    @Modifying
    @Query("""
            update MediaItem m
            set m.faceIndexingStatus = :failed, m.faceIndexingError = :error
            where m.faceIndexingStatus = :indexing
            """)
    int failStuckFaceIndexing(@Param("indexing") IndexingStatus indexing,
                              @Param("failed") IndexingStatus failed,
                              @Param("error") String error);
}
