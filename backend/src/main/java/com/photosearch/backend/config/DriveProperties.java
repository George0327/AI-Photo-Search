package com.photosearch.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.drive")
public class DriveProperties {

    /**
     * App name sent to Google APIs.
     */
    private String applicationName = "AIPhotoSearch";

    /**
     * OAuth client used by the backend to access the repository Drive account.
     * This can be the same client as your login client, but keep it separate in config.
     */
    private String clientId;
    private String clientSecret;
    private String refreshToken;

    /**
     * Prefer rootFolderId once you know it.
     * Until then, rootFolderName can be used to resolve it.
     */
    private String rootFolderId;
    private String rootFolderName = "AIPhotoSearch";

    /**
     * Page size for Drive listing.
     */
    private Integer pageSize = 1000;
}