package com.photosearch.search.ai.search;

import com.photosearch.search.ai.client.AiServiceClient;
import com.photosearch.search.ai.dto.AiSearchResponse;
import com.photosearch.search.ai.dto.AiSearchResult;
import com.photosearch.search.album.Album;
import com.photosearch.search.media.IndexingStatus;
import com.photosearch.search.media.MediaItem;
import com.photosearch.search.media.MediaItemRepository;
import com.photosearch.search.saved.SavedSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhotoSearchServiceTest {

    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final MediaItemRepository mediaItemRepository = mock(MediaItemRepository.class);
    private final SavedSearchService savedSearchService = mock(SavedSearchService.class);
    private final PhotoSearchService service =
            new PhotoSearchService(aiServiceClient, mediaItemRepository, savedSearchService);

    private static MediaItem mediaItem(String driveFileId, String albumFolderId) {
        Album album = Album.builder().driveFolderId(albumFolderId).title("Album").build();
        return MediaItem.builder()
                .driveFileId(driveFileId)
                .album(album)
                .filename(driveFileId + ".jpg")
                .mimeType("image/jpeg")
                .indexingStatus(IndexingStatus.INDEXED)
                .build();
    }

    private void stubAiResults(AiSearchResult... results) {
        when(aiServiceClient.search(anyString(), anyInt(), anyList(), any()))
                .thenReturn(new AiSearchResponse(true, "ok", "query", results.length, List.of(results)));
    }

    @Test
    void keepsAiRankingAndSkipsUnknownFiles() {
        stubAiResults(
                new AiSearchResult("file-a", "album-1", 0.9),
                new AiSearchResult("file-b", "album-1", 0.8),
                new AiSearchResult("file-c", "album-2", 0.7));
        // file-b is missing from the DB (e.g. deleted after indexing)
        when(mediaItemRepository.findSearchableByDriveFileIds(anyList(), eq(IndexingStatus.INDEXED)))
                .thenReturn(List.of(mediaItem("file-a", "album-1"), mediaItem("file-c", "album-2")));

        var response = service.searchPhotos("query", 2, null, null);

        assertEquals(2, response.results().size());
        assertEquals("file-a", response.results().get(0).file().getId());
        assertEquals(0.9, response.results().get(0).score());
        assertEquals("file-c", response.results().get(1).file().getId());
    }

    @Test
    void oversamplesTheAiRequestCappedAtOneThousand() {
        stubAiResults();
        service.searchPhotos("query", 10, null, null);
        verify(aiServiceClient).search(eq("query"), eq(30), anyList(), any());

        service.searchPhotos("query", 50, null, null);
        verify(aiServiceClient).search(eq("query"), eq(150), anyList(), any());

        service.searchPhotos("query", 500, null, null);
        verify(aiServiceClient).search(eq("query"), eq(1000), anyList(), any());
    }

    @Test
    void filtersResultsToSelectedAlbums() {
        stubAiResults(
                new AiSearchResult("file-a", "album-1", 0.9),
                new AiSearchResult("file-c", "album-2", 0.7));
        when(mediaItemRepository.findSearchableByDriveFileIds(anyList(), eq(IndexingStatus.INDEXED)))
                .thenReturn(List.of(mediaItem("file-a", "album-1"), mediaItem("file-c", "album-2")));

        var response = service.searchPhotos("query", 10, List.of("album-2"), null);

        assertEquals(1, response.results().size());
        assertEquals("file-c", response.results().get(0).file().getId());
    }

    @Test
    void returnsEmptyResponseWhenAiHasNoMatches() {
        when(aiServiceClient.search(anyString(), anyInt(), anyList(), any()))
                .thenReturn(new AiSearchResponse(true, "ok", "query", 10, List.of()));

        var response = service.searchPhotos("query", 10, null, null);

        assertTrue(response.results().isEmpty());
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void failsLoudlyWhenAiServiceReturnsNothing() {
        when(aiServiceClient.search(anyString(), anyInt(), anyList(), any())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.searchPhotos("query", 10, null, null));
    }
}
