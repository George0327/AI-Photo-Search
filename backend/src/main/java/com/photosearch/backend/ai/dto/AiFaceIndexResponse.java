package com.photosearch.backend.ai.dto;

public record AiFaceIndexResponse(
        boolean success,
        int faceCount,
        String message
) {}
