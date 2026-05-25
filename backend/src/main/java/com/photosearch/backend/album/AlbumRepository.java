package com.photosearch.backend.album;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByDriveFolderId(String driveFolderId);
    List<Album> findByEnabledTrueOrderByTitleAsc();
    boolean existsByDriveFolderId(String driveFolderId);
}