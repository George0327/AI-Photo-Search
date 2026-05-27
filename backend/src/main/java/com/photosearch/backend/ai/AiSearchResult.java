package com.photosearch.backend.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSearchResult(
        @JsonProperty("item_id")
        String itemId,

        double score
) {
}