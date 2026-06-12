package com.photosearch.search.ai.search;

import com.photosearch.search.ai.client.AiServiceClient;
import com.photosearch.search.ai.dto.AiFaceSearchResponse;
import com.photosearch.search.ai.dto.AiFaceSearchResult;
import com.photosearch.search.drive.DriveFileInfo;
import com.photosearch.search.media.MediaItem;
import com.photosearch.search.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FaceSearchService {

    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;

    public FaceSearchResponse searchByFace(List<MultipartFile> referenceFiles,
                                            List<String> albumIds, int topK) throws IOException {
        List<byte[]> imageBytesList = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        for (MultipartFile file : referenceFiles) {
            imageBytesList.add(file.getBytes());
            fileNames.add(file.getOriginalFilename() != null ? file.getOriginalFilename() : "reference.jpg");
        }

        AiFaceSearchResponse aiResponse = aiServiceClient.searchByFace(
                imageBytesList, fileNames, albumIds != null ? albumIds : List.of(), topK);

        if (aiResponse == null || !aiResponse.success()
                || aiResponse.results() == null || aiResponse.results().isEmpty()) {
            return new FaceSearchResponse(
                    aiResponse != null ? aiResponse.message() : "No response from AI service.", List.of());
        }

        List<String> driveFileIds = aiResponse.results().stream()
                .map(AiFaceSearchResult::itemId).distinct().toList();
        // Unlike photo search, no IndexingStatus.INDEXED filter here: that status
        // tracks CLIP indexing only, while face indexing is tracked per album.
        // A photo whose CLIP indexing failed can still be legitimately face-indexed,
        // so the only guard needed is that the photo still exists in Drive.
        Map<String, MediaItem> mediaMap = mediaItemRepository.findActiveByDriveFileIdIn(driveFileIds)
                .stream().collect(Collectors.toMap(MediaItem::getDriveFileId, Function.identity()));

        List<FaceSearchResult> results = new ArrayList<>();
        for (AiFaceSearchResult r : aiResponse.results()) {
            if (results.size() >= topK) break;
            MediaItem item = mediaMap.get(r.itemId());
            if (item == null) continue;
            results.add(new FaceSearchResult(r.score(), item.getAlbum().getDriveFolderId(),
                    driveFileInfoFrom(item), r.matchedBbox()));
        }
        return new FaceSearchResponse(aiResponse.message(), results);
    }

    private DriveFileInfo driveFileInfoFrom(MediaItem item) {
        return DriveFileInfo.builder()
                .id(item.getDriveFileId()).name(item.getFilename()).mimeType(item.getMimeType())
                .parentId(item.getAlbum().getDriveFolderId()).thumbnailLink(item.getDriveThumbnailLink())
                .createdTime(item.getCreatedInDriveAt()).modifiedTime(item.getModifiedInDriveAt())
                .trashed(false).folder(false).build();
    }

    public record FaceSearchResponse(String message, List<FaceSearchResult> results) {}
    public record FaceSearchResult(double score, String albumId, DriveFileInfo file, List<Integer> matchedBbox) {}
}
