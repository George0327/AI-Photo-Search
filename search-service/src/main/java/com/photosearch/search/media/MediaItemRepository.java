package com.photosearch.search.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    @Query("""
            select m from MediaItem m
            join fetch m.album a
            where m.driveFileId in :driveFileIds
              and m.deletedInDrive = false
              and m.indexingStatus = :status
            """)
    List<MediaItem> findSearchableByDriveFileIds(
            @Param("driveFileIds") Collection<String> driveFileIds,
            @Param("status") IndexingStatus status
    );

    @Query("""
            select m from MediaItem m
            join fetch m.album
            where m.driveFileId in :driveFileIds
              and m.deletedInDrive = false
            """)
    List<MediaItem> findActiveByDriveFileIdIn(@Param("driveFileIds") Collection<String> driveFileIds);
}
