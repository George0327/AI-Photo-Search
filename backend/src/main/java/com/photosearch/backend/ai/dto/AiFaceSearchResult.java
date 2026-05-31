package com.photosearch.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiFaceSearchResult(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("album_id") String albumId,
        double score,
        @JsonProperty("matched_bbox") List<Integer> matchedBbox
) {}
