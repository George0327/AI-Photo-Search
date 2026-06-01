package com.photosearch.search.drive;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @Builder
public class DriveFileInfo {
    private String id;
    private String name;
    private String mimeType;
    private String parentId;
    private Instant createdTime;
    private Instant modifiedTime;
    private boolean trashed;
    private boolean folder;
    private String thumbnailLink;
}
