package com.photosearch.backend.ai.dto;

import java.util.List;

public record AiLogoSearchResponse(
        boolean success,
        String message,
        List<AiLogoSearchResult> results
) {}
