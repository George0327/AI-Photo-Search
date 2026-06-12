package com.photosearch.indexing.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiFaceBatchIndexItemResult(
        @JsonProperty("image_id") String imageId,
        boolean success,
        @JsonProperty("face_count") int faceCount,
        String message
) {}
