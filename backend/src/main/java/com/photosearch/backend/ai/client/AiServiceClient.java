package com.photosearch.backend.ai.client;

import com.photosearch.backend.ai.dto.AiIndexResponse;
import com.photosearch.backend.ai.dto.AiSearchRequest;
import com.photosearch.backend.ai.dto.AiSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class AiServiceClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    private final AiServiceProperties properties;
    private final RestTemplate restTemplate;

    public AiServiceClient(AiServiceProperties properties,
                           @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public AiIndexResponse resetAlbumIndex(String albumId) {
        HttpHeaders headers = createInternalHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/reset",
                request,
                AiIndexResponse.class
        );
    }

    public AiIndexResponse indexAlbumImage(
            String albumId,
            String imageId,
            String fileName,
            String mimeType,
            byte[] imageBytes
    ) {
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }

            @Override
            public long contentLength() {
                return imageBytes.length;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(
                mimeType != null
                        ? MediaType.parseMediaType(mimeType)
                        : MediaType.APPLICATION_OCTET_STREAM
        );

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(imageResource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        body.add("image_id", imageId);

        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/stream",
                request,
                AiIndexResponse.class
        );
    }

    public AiSearchResponse search(String query, int topK, List<String> albumIds) {
        AiSearchRequest body = new AiSearchRequest(query, topK, albumIds);

        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AiSearchRequest> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                properties.getBaseUrl() + "/search",
                request,
                AiSearchResponse.class
        );
    }

    private HttpHeaders createInternalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey());
        return headers;
    }
}
