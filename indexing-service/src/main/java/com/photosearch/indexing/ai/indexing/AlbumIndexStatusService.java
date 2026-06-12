package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItemRepository;
import com.photosearch.indexing.media.MediaStatusCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.photosearch.indexing.ai.indexing.IndexingConstants.NEEDS_INDEXING_STATUSES;

@Service
@RequiredArgsConstructor
public class AlbumIndexStatusService {

    private final MediaItemRepository mediaItemRepository;

    public List<AlbumIndexStatus> buildAlbumIndexStatuses(List<Album> albums) {
        if (albums.isEmpty()) return List.of();
        Map<Long, List<MediaStatusCount>> countsByAlbum = groupedCounts(albums);
        return albums.stream()
                .map(album -> buildFromCounts(album, countsByAlbum.getOrDefault(album.getId(), List.of())))
                .toList();
    }

    public DriveIndexResult buildDriveIndexResult(int albumCount, List<Album> albums,
                                                   int newlyIndexedCount, List<DriveIndexFailure> failures) {
        long totalActive = 0, indexed = 0, needsIndex = 0, deleted = 0;
        if (!albums.isEmpty()) {
            for (List<MediaStatusCount> counts : groupedCounts(albums).values()) {
                totalActive += sum(counts, c -> !c.getDeleted());
                indexed += sum(counts, c -> !c.getDeleted() && c.getStatus() == IndexingStatus.INDEXED);
                needsIndex += sum(counts, c -> !c.getDeleted() && NEEDS_INDEXING_STATUSES.contains(c.getStatus()));
                deleted += sum(counts, MediaStatusCount::getDeleted);
            }
        }
        return new DriveIndexResult(albumCount, totalActive, indexed, needsIndex, deleted,
                newlyIndexedCount, failures.size(), failures);
    }

    private Map<Long, List<MediaStatusCount>> groupedCounts(List<Album> albums) {
        return mediaItemRepository.countGroupedByAlbums(albums).stream()
                .collect(Collectors.groupingBy(MediaStatusCount::getAlbumId));
    }

    private AlbumIndexStatus buildFromCounts(Album album, List<MediaStatusCount> counts) {
        long totalActiveImages = sum(counts, c -> !c.getDeleted());
        long indexedImages = sum(counts, c -> !c.getDeleted() && c.getStatus() == IndexingStatus.INDEXED);
        long needsIndexImages = sum(counts, c -> !c.getDeleted() && NEEDS_INDEXING_STATUSES.contains(c.getStatus()));
        long deletedImages = sum(counts, MediaStatusCount::getDeleted);
        long indexingImages = sum(counts, c -> !c.getDeleted() && c.getStatus() == IndexingStatus.INDEXING);

        long faceIndexedImages = sum(counts, c -> !c.getDeleted() && c.getFaceStatus() == IndexingStatus.INDEXED);
        long faceNeedsIndexImages = sum(counts, c -> !c.getDeleted() && NEEDS_INDEXING_STATUSES.contains(c.getFaceStatus()));
        long faceIndexingImages = sum(counts, c -> !c.getDeleted() && c.getFaceStatus() == IndexingStatus.INDEXING);

        return new AlbumIndexStatus(album.getId(), album.getDriveFolderId(), album.getTitle(), album.getYear(),
                totalActiveImages, indexedImages, needsIndexImages, deletedImages,
                deletedImages > 0, indexingImages > 0,
                faceIndexedImages, faceNeedsIndexImages,
                album.isIndexingFaces() || faceIndexingImages > 0,
                album.getFaceIndexedAt());
    }

    private static long sum(List<MediaStatusCount> counts, java.util.function.Predicate<MediaStatusCount> filter) {
        return counts.stream().filter(filter).mapToLong(MediaStatusCount::getTotal).sum();
    }
}
