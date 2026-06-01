package com.photosearch.search.ai.dto;

import java.util.List;

public record AiFaceSearchResponse(boolean success, String message, List<AiFaceSearchResult> results) {}
