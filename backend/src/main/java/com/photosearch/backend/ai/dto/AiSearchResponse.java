package com.photosearch.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiSearchResponse(
        boolean success,
        String message,
        String query,
        @JsonProperty("top_k")
        int topK,
        List<AiSearchResult> results
) {
}