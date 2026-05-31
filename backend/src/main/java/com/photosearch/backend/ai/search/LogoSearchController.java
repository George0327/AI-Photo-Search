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
public class LogoSearchController {

    private final LogoSearchService logoSearchService;

    @PostMapping(value = "/logos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LogoSearchService.LogoSearchResponse searchLogos(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) List<String> albumIds,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int topK
    ) throws IOException {
        return logoSearchService.searchByLogo(files, albumIds, topK);
    }
}
