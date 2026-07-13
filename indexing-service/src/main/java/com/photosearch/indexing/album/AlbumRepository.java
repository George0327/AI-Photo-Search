package com.photosearch.indexing.album;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByDriveFolderId(String driveFolderId);
    List<Album> findByEnabledTrueAndDeletedInDriveFalseOrderByTitleAsc();
    List<Album> findByDeletedInDriveFalseOrderByTitleAsc();
    boolean existsByDriveFolderId(String driveFolderId);

    // clears in-progress flags left behind by a crash/restart
    @Modifying
    @Query("update Album a set a.indexingFaces = false where a.indexingFaces = true")
    int clearStuckFaceIndexingFlags();
}
