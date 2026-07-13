package com.photosearch.search.ai.search;

import com.photosearch.search.ai.client.AiServiceClient;
import com.photosearch.search.ai.dto.AiSearchResponse;
import com.photosearch.search.ai.dto.AiSearchResult;
import com.photosearch.search.drive.DriveFileInfo;
import com.photosearch.search.drive.PhotoNaming;
import com.photosearch.search.media.IndexingStatus;
import com.photosearch.search.media.MediaItem;
import com.photosearch.search.media.MediaItemRepository;
import com.photosearch.search.saved.SavedSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoSearchService {

    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;
    private final SavedSearchService savedSearchService;

    // the AI service ranks the photos, then their details are hydrated from the database
    public PhotoSearchResponse searchPhotos(String query, int topK, List<String> albumIds, List<String> restrictToIds) {
        List<String> selectedAlbumIds = albumIds != null ? albumIds : List.of();

        // over-fetch, since hydration drops the photos that are gone from the database
        int aiTopK = Math.min(topK * 3, 1000);

        AiSearchResponse aiResponse = aiServiceClient.search(query, aiTopK, selectedAlbumIds, restrictToIds);

        if (aiResponse == null) throw new IllegalStateException("No response from AI service");

        if (!aiResponse.success() || aiResponse.results() == null || aiResponse.results().isEmpty())
            return new PhotoSearchResponse(query, topK, selectedAlbumIds, List.of());

        List<String> driveFileIds = aiResponse.results().stream()
                .map(AiSearchResult::itemId).distinct().toList();

        if (driveFileIds.isEmpty()) return new PhotoSearchResponse(query, topK, selectedAlbumIds, List.of());

        Map<String, MediaItem> mediaByDriveFileId = mediaItemRepository
                .findSearchableByDriveFileIds(driveFileIds, IndexingStatus.INDEXED)
                .stream().collect(Collectors.toMap(MediaItem::getDriveFileId, Function.identity()));

        Set<String> selectedAlbumIdSet = new HashSet<>(selectedAlbumIds);
        List<PhotoSearchResult> results = new ArrayList<>();

        for (AiSearchResult result : aiResponse.results()) {
            if (results.size() >= topK) break;

            MediaItem mediaItem = mediaByDriveFileId.get(result.itemId());

            if (mediaItem == null) continue;

            String albumDriveFolderId = mediaItem.getAlbum().getDriveFolderId();

            if (!selectedAlbumIdSet.isEmpty() && !selectedAlbumIdSet.contains(albumDriveFolderId)) continue;

            results.add(new PhotoSearchResult(result.score(), albumDriveFolderId, driveFileInfoFrom(mediaItem)));
        }

        savedSearchService.save("TEXT", query, selectedAlbumIds,
                results.stream().map(r -> r.file().getId()).toList());

        return new PhotoSearchResponse(query, topK, selectedAlbumIds, results);
    }

    private DriveFileInfo driveFileInfoFrom(MediaItem item) {
        return DriveFileInfo.builder()
                .id(item.getDriveFileId())
                .name(PhotoNaming.displayName(item.getAlbum().getTitle(), item.getFilename()))
                .mimeType(item.getMimeType())
                .parentId(item.getAlbum().getDriveFolderId()).thumbnailLink(item.getDriveThumbnailLink())
                .createdTime(item.getCreatedInDriveAt()).modifiedTime(item.getModifiedInDriveAt())
                .trashed(false).folder(false).build();
    }

    public record PhotoSearchResponse(String query, int topK, List<String> albumIds, List<PhotoSearchResult> results) {}
    public record PhotoSearchResult(double score, String albumId, DriveFileInfo file) {}
}
