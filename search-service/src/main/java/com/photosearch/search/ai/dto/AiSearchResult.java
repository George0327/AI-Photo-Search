package com.photosearch.search.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSearchResult(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("album_id") String albumId,
        double score
) {}
