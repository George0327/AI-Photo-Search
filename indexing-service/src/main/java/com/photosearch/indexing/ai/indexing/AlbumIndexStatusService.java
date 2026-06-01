package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.photosearch.indexing.ai.indexing.IndexingConstants.NEEDS_INDEXING_STATUSES;

@Service
@RequiredArgsConstructor
public class AlbumIndexStatusService {

    private final MediaItemRepository mediaItemRepository;

    public AlbumIndexStatus buildAlbumIndexStatus(Album album) {
        long totalActiveImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);
        long indexedImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatus(album, IndexingStatus.INDEXED);
        long needsIndexImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(album, NEEDS_INDEXING_STATUSES);
        long deletedImages = mediaItemRepository.countByAlbumAndDeletedInDriveTrue(album);
        long indexingImages = mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatus(album, IndexingStatus.INDEXING);

        return new AlbumIndexStatus(album.getId(), album.getDriveFolderId(), album.getTitle(),
                totalActiveImages, indexedImages, needsIndexImages, deletedImages,
                deletedImages > 0, indexingImages > 0, album.getFaceIndexedAt(), album.getLogoIndexedAt());
    }

    public DriveIndexResult buildDriveIndexResult(int albumCount, List<Album> albums,
                                                   int newlyIndexedCount, List<DriveIndexFailure> failures) {
        long totalActive = 0, indexed = 0, needsIndex = 0, deleted = 0;
        for (Album album : albums) {
            totalActive += mediaItemRepository.countByAlbumAndDeletedInDriveFalse(album);
            indexed += mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatus(album, IndexingStatus.INDEXED);
            needsIndex += mediaItemRepository.countByAlbumAndDeletedInDriveFalseAndIndexingStatusIn(album, NEEDS_INDEXING_STATUSES);
            deleted += mediaItemRepository.countByAlbumAndDeletedInDriveTrue(album);
        }
        return new DriveIndexResult(albumCount, totalActive, indexed, needsIndex, deleted,
                newlyIndexedCount, failures.size(), failures);
    }
}
