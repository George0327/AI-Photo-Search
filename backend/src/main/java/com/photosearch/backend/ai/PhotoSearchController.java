package com.photosearch.backend.ai;

import com.photosearch.backend.drive.DriveFileInfo;
import com.photosearch.backend.drive.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class PhotoSearchController {

    private final AiServiceClient aiServiceClient;
    private final GoogleDriveService googleDriveService;

    @GetMapping
    public PhotoSearchResponse searchPhotos(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK
    ) {
        AiSearchResponse aiResponse = aiServiceClient.search(query, topK);

        if (aiResponse == null || !aiResponse.success()) {
            throw new IllegalStateException(
                    aiResponse != null ? aiResponse.message() : "No response from AI service"
            );
        }

        List<PhotoSearchResult> results = aiResponse.results()
                .stream()
                .map(result -> {
                    DriveFileInfo fileInfo = googleDriveService.getFileInfo(result.itemId());

                    return new PhotoSearchResult(
                            result.score(),
                            fileInfo
                    );
                })
                .toList();

        return new PhotoSearchResponse(
                query,
                topK,
                results
        );
    }

    public record PhotoSearchResponse(
            String query,
            int topK,
            List<PhotoSearchResult> results
    ) {
    }

    public record PhotoSearchResult(
            double score,
            DriveFileInfo file
    ) {
    }
}