package com.photosearch.search.ai.search;

import com.photosearch.search.ai.client.AiServiceClient;
import com.photosearch.search.ai.dto.AiLogoSearchResponse;
import com.photosearch.search.ai.dto.AiLogoSearchResult;
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
public class LogoSearchService {

    private final AiServiceClient aiServiceClient;
    private final MediaItemRepository mediaItemRepository;

    public LogoSearchResponse searchByLogo(List<MultipartFile> referenceFiles,
                                            List<String> albumIds, int topK) throws IOException {
        List<byte[]> imageBytesList = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        for (MultipartFile file : referenceFiles) {
            imageBytesList.add(file.getBytes());
            fileNames.add(file.getOriginalFilename() != null ? file.getOriginalFilename() : "logo.jpg");
        }

        AiLogoSearchResponse aiResponse = aiServiceClient.searchByLogo(
                imageBytesList, fileNames, albumIds != null ? albumIds : List.of(), topK);

        if (aiResponse == null || !aiResponse.success() || aiResponse.results().isEmpty()) {
            return new LogoSearchResponse(
                    aiResponse != null ? aiResponse.message() : "No response from AI service.", List.of());
        }

        List<String> driveFileIds = aiResponse.results().stream()
                .map(AiLogoSearchResult::itemId).distinct().toList();
        Map<String, MediaItem> mediaMap = mediaItemRepository.findActiveByDriveFileIdIn(driveFileIds)
                .stream().collect(Collectors.toMap(MediaItem::getDriveFileId, Function.identity()));

        List<LogoSearchResult> results = new ArrayList<>();
        for (AiLogoSearchResult r : aiResponse.results()) {
            if (results.size() >= topK) break;
            MediaItem item = mediaMap.get(r.itemId());
            if (item == null) continue;
            results.add(new LogoSearchResult(r.score(), item.getAlbum().getDriveFolderId(),
                    driveFileInfoFrom(item), r.matchedBbox()));
        }
        return new LogoSearchResponse(aiResponse.message(), results);
    }

    private DriveFileInfo driveFileInfoFrom(MediaItem item) {
        return DriveFileInfo.builder()
                .id(item.getDriveFileId()).name(item.getFilename()).mimeType(item.getMimeType())
                .parentId(item.getAlbum().getDriveFolderId()).thumbnailLink(item.getDriveThumbnailLink())
                .createdTime(item.getCreatedInDriveAt()).modifiedTime(item.getModifiedInDriveAt())
                .trashed(false).folder(false).build();
    }

    public record LogoSearchResponse(String message, List<LogoSearchResult> results) {}
    public record LogoSearchResult(double score, String albumId, DriveFileInfo file, List<Integer> matchedBbox) {}
}
