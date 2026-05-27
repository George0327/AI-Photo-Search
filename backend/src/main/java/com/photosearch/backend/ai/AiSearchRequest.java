package com.photosearch.backend.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSearchRequest(
        String query,

        @JsonProperty("top_k")
        int topK
) {
}