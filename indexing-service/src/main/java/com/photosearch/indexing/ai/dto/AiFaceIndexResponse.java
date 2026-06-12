package com.photosearch.indexing.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiFaceIndexResponse(boolean success, @JsonProperty("face_count") int faceCount, String message) {}
