package com.photosearch.backend.ai.dto;

public record AiLogoIndexResponse(
        boolean success,
        int logoRegionCount,
        String message
) {}
