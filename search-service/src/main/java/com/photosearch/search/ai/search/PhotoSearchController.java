package com.photosearch.search.ai.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    /** Plain semantic search (bookmarkable via query params). */
    @GetMapping
    public PhotoSearchService.PhotoSearchResponse searchPhotos(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(1000) int topK,
            @RequestParam(required = false) List<String> albumIds
    ) {
        return photoSearchService.searchPhotos(query, topK, albumIds, null);
    }

    /** Semantic search with an optional "search within results" restriction. POST because restrictToIds can carry up to 1000 IDs (too long for a URL). */
    @PostMapping
    public PhotoSearchService.PhotoSearchResponse searchPhotosPost(@Valid @RequestBody PhotoSearchRequest req) {
        return photoSearchService.searchPhotos(req.query(), req.topK(),
                req.albumIds(), req.restrictToIds());
    }

    public record PhotoSearchRequest(
            @NotBlank String query,
            @Min(1) @Max(1000) int topK,
            List<String> albumIds,
            List<String> restrictToIds
    ) {}
}
