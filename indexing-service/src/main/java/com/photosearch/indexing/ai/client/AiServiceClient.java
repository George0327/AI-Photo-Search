package com.photosearch.indexing.ai.client;

import com.photosearch.indexing.ai.dto.AiBatchIndexResponse;
import com.photosearch.indexing.ai.dto.AiFaceBatchIndexResponse;
import com.photosearch.indexing.ai.dto.AiFaceIndexResponse;
import com.photosearch.indexing.ai.dto.AiImagePayload;
import com.photosearch.indexing.ai.dto.AiIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Supplier;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1_000;

    private final AiServiceProperties properties;
    private final RestTemplate restTemplate;

    public AiServiceClient(AiServiceProperties properties,
                           @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public AiIndexResponse resetAlbumIndex(String albumId) {
        HttpEntity<Void> request = new HttpEntity<>(createInternalHeaders());
        return withRetry("reset album index", () -> restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/reset",
                request, AiIndexResponse.class));
    }

    public AiIndexResponse resetAlbumFaceIndex(String albumId) {
        HttpEntity<Void> request = new HttpEntity<>(createInternalHeaders());
        return withRetry("reset album face index", () -> restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/faces/reset",
                request, AiIndexResponse.class));
    }

    public AiBatchIndexResponse indexAlbumImageBatch(String albumId, List<AiImagePayload> images) {
        HttpEntity<MultiValueMap<String, Object>> request = buildBatchMultipart(images);
        return withRetry("index image batch", () -> restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/batch",
                request, AiBatchIndexResponse.class));
    }

    public AiFaceBatchIndexResponse indexAlbumFacesBatch(String albumId, List<AiImagePayload> images) {
        HttpEntity<MultiValueMap<String, Object>> request = buildBatchMultipart(images);
        return withRetry("index face batch", () -> restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/faces/batch",
                request, AiFaceBatchIndexResponse.class));
    }

    public AiFaceIndexResponse indexAlbumFaces(String albumId, String imageId,
            String fileName, String mimeType, byte[] imageBytes) {
        HttpEntity<MultiValueMap<String, Object>> request =
                buildImageMultipart(imageId, fileName, mimeType, imageBytes);
        return withRetry("index faces", () -> restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/faces",
                request, AiFaceIndexResponse.class));
    }

    /**
     * Retries transient failures (connection problems, AI service 5xx) with
     * exponential backoff. Client errors (4xx) are not retried.
     */
    private <T> T withRetry(String operation, Supplier<T> call) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException | HttpServerErrorException e) {
                if (attempt >= MAX_ATTEMPTS) throw e;
                log.warn("AI service call '{}' failed (attempt {}/{}): {} — retrying in {} ms",
                        operation, attempt, MAX_ATTEMPTS, e.getMessage(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoffMs *= 2;
            }
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> buildBatchMultipart(List<AiImagePayload> images) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (AiImagePayload image : images) {
            body.add("files", new HttpEntity<>(fileResource(image.fileName(), image.bytes()),
                    fileHeaders(image.mimeType())));
            body.add("image_ids", image.imageId());
        }
        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildImageMultipart(
            String imageId, String fileName, String mimeType, byte[] imageBytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(fileResource(fileName, imageBytes), fileHeaders(mimeType)));
        body.add("image_id", imageId);

        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private static ByteArrayResource fileResource(String fileName, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
            @Override public long contentLength() { return bytes.length; }
        };
    }

    private static HttpHeaders fileHeaders(String mimeType) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mimeType != null
                ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM);
        return fileHeaders;
    }

    private HttpHeaders createInternalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey());
        return headers;
    }
}
