package com.photosearch.indexing.ai.dto;

import java.util.List;

public record AiBatchIndexResponse(boolean success, String message, List<AiBatchIndexItemResult> results) {}
