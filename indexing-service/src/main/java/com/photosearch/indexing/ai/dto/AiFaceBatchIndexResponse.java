package com.photosearch.indexing.ai.dto;

import java.util.List;

public record AiFaceBatchIndexResponse(boolean success, String message, List<AiFaceBatchIndexItemResult> results) {}
