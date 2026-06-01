package com.photosearch.indexing.ai.client;

import com.photosearch.indexing.ai.dto.AiFaceIndexResponse;
import com.photosearch.indexing.ai.dto.AiIndexResponse;
import com.photosearch.indexing.ai.dto.AiLogoIndexResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
                request, AiIndexResponse.class);
    }

    public AiIndexResponse indexAlbumImage(String albumId, String imageId,
            String fileName, String mimeType, byte[] imageBytes) {
        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override public String getFilename() { return fileName; }
            @Override public long contentLength() { return imageBytes.length; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mimeType != null
                ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        body.add("image_id", imageId);

        HttpHeaders headers = createInternalHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/stream",
                new HttpEntity<>(body, headers), AiIndexResponse.class);
    }

    public AiFaceIndexResponse indexAlbumFaces(String albumId, String imageId,
            String fileName, String mimeType, byte[] imageBytes) {
        return restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/faces",
                buildImageMultipart(imageId, fileName, mimeType, imageBytes),
                AiFaceIndexResponse.class);
    }

    public AiLogoIndexResponse indexAlbumLogos(String albumId, String imageId,
            String fileName, String mimeType, byte[] imageBytes) {
        return restTemplate.postForObject(
                properties.getBaseUrl() + "/index/albums/" + albumId + "/logos",
                buildImageMultipart(imageId, fileName, mimeType, imageBytes),
                AiLogoIndexResponse.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildImageMultipart(
            String imageId, String fileName, String mimeType, byte[] imageBytes) {
        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override public String getFilename() { return fileName; }
            @Override public long contentLength() { return imageBytes.length; }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mimeType != null
                ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        body.add("image_id", imageId);

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
