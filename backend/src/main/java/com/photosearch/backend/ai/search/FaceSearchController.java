package com.photosearch.backend.ai.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class FaceSearchController {

    private final FaceSearchService faceSearchService;

    @PostMapping(value = "/faces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceSearchService.FaceSearchResponse searchFaces(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) List<String> albumIds,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int topK
    ) throws IOException {
        return faceSearchService.searchByFace(files, albumIds, topK);
    }
}
