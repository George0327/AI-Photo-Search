package com.photosearch.backend.media;

import com.photosearch.backend.album.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {
    Optional<MediaItem> findByDriveFileId(String driveFileId);
    List<MediaItem> findByAlbumAndDeletedInDriveFalse(Album album);
    List<MediaItem> findByAlbumIdInAndDeletedInDriveFalseAndIndexedInFaissTrue(Collection<Long> albumIds);
    List<MediaItem> findByDeletedInDriveFalseAndIndexedInFaissTrue();
    boolean existsByDriveFileId(String driveFileId);
}