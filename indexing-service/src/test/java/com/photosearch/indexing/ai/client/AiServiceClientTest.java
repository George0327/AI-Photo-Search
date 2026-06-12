package com.photosearch.indexing.ai.client;

import com.photosearch.indexing.ai.dto.AiIndexResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiServiceClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AiServiceClient client = new AiServiceClient(properties(), restTemplate);

    private static AiServiceProperties properties() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl("http://ai-service:8000");
        properties.setInternalApiKey("test-key");
        return properties;
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        AiIndexResponse expected = new AiIndexResponse(true, "ok");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(AiIndexResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"))
                .thenThrow(new ResourceAccessException("connection refused"))
                .thenReturn(expected);

        AiIndexResponse response = client.resetAlbumIndex("album-1");

        assertEquals(expected, response);
        verify(restTemplate, times(3))
                .postForObject(anyString(), any(HttpEntity.class), eq(AiIndexResponse.class));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(AiIndexResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThrows(ResourceAccessException.class, () -> client.resetAlbumIndex("album-1"));
        verify(restTemplate, times(3))
                .postForObject(anyString(), any(HttpEntity.class), eq(AiIndexResponse.class));
    }

    @Test
    void doesNotRetryClientErrors() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(AiIndexResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.class, () -> client.resetAlbumIndex("album-1"));
        verify(restTemplate, times(1))
                .postForObject(anyString(), any(HttpEntity.class), eq(AiIndexResponse.class));
    }
}
