package com.photosearch.indexing.ai.indexing;

import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.media.IndexingStatus;
import com.photosearch.indexing.media.MediaItemRepository;
import com.photosearch.indexing.media.MediaStatusCount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AlbumIndexStatusServiceTest {

    private final MediaItemRepository repository = mock(MediaItemRepository.class);
    private final AlbumIndexStatusService service = new AlbumIndexStatusService(repository);

    private static MediaStatusCount count(long albumId, boolean deleted,
                                          IndexingStatus status, IndexingStatus faceStatus, long total) {
        return new MediaStatusCount() {
            @Override public Long getAlbumId() { return albumId; }
            @Override public boolean getDeleted() { return deleted; }
            @Override public IndexingStatus getStatus() { return status; }
            @Override public IndexingStatus getFaceStatus() { return faceStatus; }
            @Override public long getTotal() { return total; }
        };
    }

    private static Album album(long id, String driveFolderId) {
        return Album.builder().id(id).driveFolderId(driveFolderId).title("Album " + id).build();
    }

    @Test
    void buildsStatusesForAllAlbumsWithASingleQuery() {
        Album album1 = album(1L, "drive-1");
        Album album2 = album(2L, "drive-2");
        when(repository.countGroupedByAlbums(anyCollection())).thenReturn(List.of(
                count(1L, false, IndexingStatus.INDEXED, IndexingStatus.INDEXED, 8),
                count(1L, false, IndexingStatus.INDEXED, IndexingStatus.NOT_INDEXED, 2),
                count(1L, false, IndexingStatus.FAILED, IndexingStatus.FAILED, 2),
                count(1L, false, IndexingStatus.INDEXING, IndexingStatus.NOT_INDEXED, 1),
                count(1L, true, IndexingStatus.DELETED, IndexingStatus.DELETED, 3)
        ));

        List<AlbumIndexStatus> statuses = service.buildAlbumIndexStatuses(List.of(album1, album2));

        assertEquals(2, statuses.size());
        AlbumIndexStatus first = statuses.get(0);
        assertEquals(13, first.totalActiveImages());
        assertEquals(10, first.indexedImages());
        assertEquals(2, first.needsIndexImages());
        assertEquals(3, first.deletedImages());
        assertTrue(first.reindexRecommended());
        assertTrue(first.indexingImages());

        // Face counts follow the same lifecycle, computed from the same query.
        assertEquals(8, first.faceIndexedImages());
        assertEquals(5, first.faceNeedsIndexImages());
        assertFalse(first.indexingFaces());

        // Album without media rows yields all-zero counts instead of failing.
        AlbumIndexStatus second = statuses.get(1);
        assertEquals(0, second.totalActiveImages());
        assertEquals(0, second.indexedImages());
        assertEquals(0, second.faceIndexedImages());
        assertFalse(second.indexingImages());

        verify(repository, times(1)).countGroupedByAlbums(anyCollection());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyAlbumListSkipsTheQueryEntirely() {
        assertTrue(service.buildAlbumIndexStatuses(List.of()).isEmpty());
        verifyNoInteractions(repository);
    }
}
