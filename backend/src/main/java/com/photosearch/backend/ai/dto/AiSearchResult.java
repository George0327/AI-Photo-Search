package com.photosearch.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSearchResult(
        @JsonProperty("item_id")
        String itemId,
        @JsonProperty("album_id")
        String albumId,
        double score
) {
}