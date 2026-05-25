package com.photosearch.backend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DriveProperties.class)
public class DriveConfig {

    private final DriveProperties driveProperties;

    @Bean
    public Drive driveClient() {
        try {
            UserCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(driveProperties.getClientId())
                    .setClientSecret(driveProperties.getClientSecret())
                    .setRefreshToken(driveProperties.getRefreshToken())
                    .build();

            HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> {
                        credentialsAdapter.initialize(request);
                        request.setConnectTimeout(60_000);
                        request.setReadTimeout(60_000);
                    }
            )
                    .setApplicationName(driveProperties.getApplicationName())
                    .build();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Google Drive client", e);
        }
    }
}