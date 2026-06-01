package com.photosearch.search.ai.dto;

import java.util.List;

public record AiLogoSearchResponse(boolean success, String message, List<AiLogoSearchResult> results) {}
