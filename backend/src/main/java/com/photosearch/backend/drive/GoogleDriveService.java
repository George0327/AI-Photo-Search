package com.photosearch.backend.drive;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.photosearch.backend.config.DriveProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String FILE_FIELDS =
            "files(id,name,mimeType,parents,createdTime,modifiedTime,trashed),nextPageToken";

    private final Drive driveClient;
    private final DriveProperties driveProperties;

    public String resolveRootFolderId() {
        if (hasText(driveProperties.getRootFolderId())) {
            return driveProperties.getRootFolderId().trim();
        }

        String query = String.format(
                "mimeType = '%s' and name = '%s' and trashed = false and 'root' in parents",
                FOLDER_MIME_TYPE,
                escape(driveProperties.getRootFolderName())
        );

        List<DriveFileInfo> folders = search(query);

        if (folders.isEmpty()) {
            throw new IllegalStateException(
                    "Drive root folder not found: " + driveProperties.getRootFolderName()
            );
        }

        if (folders.size() > 1) {
            throw new IllegalStateException(
                    "Multiple Drive root folders found with name: " + driveProperties.getRootFolderName()
                            + ". Set app.drive.root-folder-id explicitly."
            );
        }

        return folders.get(0).getId();
    }

    public List<DriveFileInfo> listAlbumFolders(String rootFolderId) {
        String query = String.format(
                "mimeType = '%s' and trashed = false and '%s' in parents",
                FOLDER_MIME_TYPE,
                rootFolderId
        );

        return search(query);
    }

    public List<DriveFileInfo> listImagesInAlbum(String albumFolderId) {
        String query = String.format(
                "trashed = false and '%s' in parents and mimeType contains 'image/'",
                albumFolderId
        );

        return search(query);
    }

    public DriveFileInfo getFileInfo(String fileId) {
        try {
            com.google.api.services.drive.model.File file = driveClient.files()
                    .get(fileId)
                    .setFields("id,name,mimeType,parents,createdTime,modifiedTime,trashed")
                    .setSupportsAllDrives(false)
                    .execute();

            return map(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get Drive file metadata: " + fileId, e);
        }
    }

    public byte[] downloadFileBytes(String fileId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            driveClient.files()
                    .get(fileId)
                    .executeMediaAndDownloadTo(outputStream);

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download Drive file: " + fileId, e);
        }
    }

    public byte[] createThumbnailBytes(String fileId, int maxSize) {
        byte[] originalBytes = downloadFileBytes(fileId);

        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));

            if (originalImage == null) {
                throw new IllegalStateException("Unsupported image format for thumbnail: " + fileId);
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            double scale = Math.min(
                    (double) maxSize / originalWidth,
                    (double) maxSize / originalHeight
            );

            if (scale > 1.0) {
                scale = 1.0;
            }

            int thumbnailWidth = Math.max(1, (int) Math.round(originalWidth * scale));
            int thumbnailHeight = Math.max(1, (int) Math.round(originalHeight * scale));

            BufferedImage thumbnail = new BufferedImage(
                    thumbnailWidth,
                    thumbnailHeight,
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D graphics = thumbnail.createGraphics();

            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                graphics.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY
                );
                graphics.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                graphics.drawImage(originalImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
            } finally {
                graphics.dispose();
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ImageIO.write(thumbnail, "jpg", outputStream);
                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create thumbnail for Drive file: " + fileId, e);
        }
    }

    private List<DriveFileInfo> search(String query) {
        try {
            List<DriveFileInfo> result = new ArrayList<>();
            String nextPageToken = null;

            do {
                FileList fileList = driveClient.files()
                        .list()
                        .setQ(query)
                        .setFields(FILE_FIELDS)
                        .setPageSize(driveProperties.getPageSize())
                        .setPageToken(nextPageToken)
                        .setSupportsAllDrives(false)
                        .setIncludeItemsFromAllDrives(false)
                        .execute();

                if (fileList.getFiles() != null) {
                    for (com.google.api.services.drive.model.File file : fileList.getFiles()) {
                        result.add(map(file));
                    }
                }

                nextPageToken = fileList.getNextPageToken();
            } while (nextPageToken != null);

            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to query Google Drive", e);
        }
    }

    private DriveFileInfo map(com.google.api.services.drive.model.File file) {
        String parentId = null;
        if (file.getParents() != null && !file.getParents().isEmpty()) {
            parentId = file.getParents().get(0);
        }

        return DriveFileInfo.builder()
                .id(file.getId())
                .name(file.getName())
                .mimeType(file.getMimeType())
                .parentId(parentId)
                .createdTime(toInstant(file.getCreatedTime() != null ? file.getCreatedTime().getValue() : null))
                .modifiedTime(toInstant(file.getModifiedTime() != null ? file.getModifiedTime().getValue() : null))
                .trashed(Boolean.TRUE.equals(file.getTrashed()))
                .folder(Objects.equals(FOLDER_MIME_TYPE, file.getMimeType()))
                .build();
    }

    private Instant toInstant(Long value) {
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String escape(String value) {
        return value.replace("'", "\\'");
    }
}