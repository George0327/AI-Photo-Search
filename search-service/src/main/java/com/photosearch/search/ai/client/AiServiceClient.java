package com.photosearch.search.ai.client;

import com.photosearch.search.ai.dto.*;
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

    public AiSearchResponse search(String query, int topK, List<String> albumIds) {
        AiSearchRequest body = new AiSearchRequest(query, topK, albumIds);
        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(properties.getBaseUrl() + "/search",
                new HttpEntity<>(body, headers), AiSearchResponse.class);
    }

    public AiFaceSearchResponse searchByFace(
            List<byte[]> referenceImages, List<String> fileNames,
            List<String> albumIds, int topK) {
        return restTemplate.postForObject(properties.getBaseUrl() + "/search/faces",
                buildDetectionSearchRequest(referenceImages, fileNames, albumIds, topK),
                AiFaceSearchResponse.class);
    }

    public AiLogoSearchResponse searchByLogo(
            List<byte[]> referenceImages, List<String> fileNames,
            List<String> albumIds, int topK) {
        return restTemplate.postForObject(properties.getBaseUrl() + "/search/logos",
                buildDetectionSearchRequest(referenceImages, fileNames, albumIds, topK),
                AiLogoSearchResponse.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildDetectionSearchRequest(
            List<byte[]> referenceImages, List<String> fileNames,
            List<String> albumIds, int topK) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (int i = 0; i < referenceImages.size(); i++) {
            final byte[] bytes = referenceImages.get(i);
            final String name = fileNames.get(i);
            HttpHeaders fh = new HttpHeaders();
            fh.setContentType(MediaType.IMAGE_JPEG);
            body.add("files", new HttpEntity<>(new ByteArrayResource(bytes) {
                @Override public String getFilename() { return name; }
                @Override public long contentLength() { return bytes.length; }
            }, fh));
        }
        albumIds.forEach(id -> body.add("album_ids", id));
        body.add("top_k", String.valueOf(topK));
        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders createInternalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey());
        return headers;
    }
}
