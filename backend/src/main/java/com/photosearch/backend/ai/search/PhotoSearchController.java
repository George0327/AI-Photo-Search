package com.photosearch.backend.ai.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class PhotoSearchController {

    private final PhotoSearchService photoSearchService;

    @GetMapping
    public PhotoSearchService.PhotoSearchResponse searchPhotos(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int topK,
            @RequestParam(required = false) List<String> albumIds
    ) {
        return photoSearchService.searchPhotos(query, topK, albumIds);
    }
}
