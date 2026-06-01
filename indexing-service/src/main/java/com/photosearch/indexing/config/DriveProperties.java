package com.photosearch.indexing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.drive")
public class DriveProperties {
    private String applicationName = "AIPhotoSearch";
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String rootFolderId;
    private String rootFolderName = "AIPhotoSearch";
    private Integer pageSize = 1000;
}
