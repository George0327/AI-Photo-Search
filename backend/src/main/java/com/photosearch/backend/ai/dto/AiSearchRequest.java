package com.photosearch.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiSearchRequest(
        String query,
        @JsonProperty("top_k")
        int topK,
        @JsonProperty("album_ids")
        List<String> albumIds
) {
}