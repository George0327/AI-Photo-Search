package com.photosearch.indexing.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiBatchIndexItemResult(
        @JsonProperty("image_id") String imageId,
        boolean success,
        String message
) {}
