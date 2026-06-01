package com.photosearch.indexing.album;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByDriveFolderId(String driveFolderId);
    List<Album> findByEnabledTrueAndDeletedInDriveFalseOrderByTitleAsc();
    List<Album> findByDeletedInDriveFalseOrderByTitleAsc();
    boolean existsByDriveFolderId(String driveFolderId);
}
