package com.photosearch.indexing.ai.dto;

// raw image bytes plus the metadata the AI service needs
public record AiImagePayload(String imageId, String fileName, String mimeType, byte[] bytes) {}
