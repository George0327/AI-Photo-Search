package com.photosearch.indexing.ai.dto;

/** Raw image bytes plus the metadata the AI service needs to index them. */
public record AiImagePayload(String imageId, String fileName, String mimeType, byte[] bytes) {}
