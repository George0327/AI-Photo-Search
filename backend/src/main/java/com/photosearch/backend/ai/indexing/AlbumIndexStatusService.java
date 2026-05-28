package com.photosearch.backend.ai.indexing;

import com.photosearch.backend.album.Album;
import com.photosearch.backend.media.IndexingStatus;
import com.photosearch.backend.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.photosearch.backend.ai.indexing.IndexingConstants.NEEDS_INDEXING_STATUSES;

@Service
@RequiredArgsConstructor
public class AlbumIndexStatusService {

    private final MediaItemRepository mediaItemRepository;

    public AlbumIndexStatus buildAlbumIndexStatus(Album album) {
        long totalActiveImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);
        long indexedImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatus(
                album, IndexingStatus.INDEXED);
        long needsIndexImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
                album, NEEDS_INDEXING_STATUSES);
        long deletedImages = mediaItemRepository.countByAlbumAndDeletedInDriveTrue(album);

        return new AlbumIndexStatus(
                album.getId(),
                album.getDriveFolderId(),
                album.getTitle(),
                totalActiveImages,
                indexedImages,
                needsIndexImages,
                deletedImages,
                deletedImages > 0
        );
    }

    public DriveIndexResult buildDriveIndexResult(
            int albumCount,
            List<Album> albums,
            int newlyIndexedCount,
            List<DriveIndexFailure> failures
    ) {
        long totalActiveImages = 0;
        long indexedImages = 0;
        long needsIndexImages = 0;
        long deletedImages = 0;

        for (Album album : albums) {
            totalActiveImages += mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);
            indexedImages += mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatus(
                    album, IndexingStatus.INDEXED);
            needsIndexImages += mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(
                    album, NEEDS_INDEXING_STATUSES);
            deletedImages += mediaItemRepository.countByAlbumAndDeletedInDriveTrue(album);
        }

        return new DriveIndexResult(
                albumCount,
                totalActiveImages,
                indexedImages,
                needsIndexImages,
                deletedImages,
                newlyIndexedCount,
                failures.size(),
                failures
        );
    }
}
