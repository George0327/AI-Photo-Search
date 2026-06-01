package com.photosearch.indexing.drive;

import com.google.api.client.http.GenericUrl;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.photosearch.indexing.config.DriveProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String FILE_FIELDS =
            "files(id,name,mimeType,parents,createdTime,modifiedTime,trashed,thumbnailLink,thumbnailVersion),nextPageToken";

    private final Drive driveClient;
    private final DriveProperties driveProperties;

    public String resolveRootFolderId() {
        if (hasText(driveProperties.getRootFolderId())) {
            return driveProperties.getRootFolderId().trim();
        }
        String query = String.format(
                "mimeType = '%s' and name = '%s' and trashed = false and 'root' in parents",
                FOLDER_MIME_TYPE, escape(driveProperties.getRootFolderName()));
        List<DriveFileInfo> folders = search(query);
        if (folders.isEmpty()) throw new IllegalStateException("Drive root folder not found: " + driveProperties.getRootFolderName());
        if (folders.size() > 1) throw new IllegalStateException("Multiple Drive root folders found: " + driveProperties.getRootFolderName());
        return folders.get(0).getId();
    }

    public List<DriveFileInfo> listAlbumFolders(String rootFolderId) {
        return search(String.format("mimeType = '%s' and trashed = false and '%s' in parents", FOLDER_MIME_TYPE, rootFolderId));
    }

    public List<DriveFileInfo> listImagesInAlbum(String albumFolderId) {
        return search(String.format("trashed = false and '%s' in parents and mimeType contains 'image/'", albumFolderId));
    }

    public DriveFileInfo getFileInfo(String fileId) {
        try {
            com.google.api.services.drive.model.File file = driveClient.files()
                    .get(fileId)
                    .setFields("id,name,mimeType,parents,createdTime,modifiedTime,trashed,thumbnailLink,thumbnailVersion")
                    .setSupportsAllDrives(false).execute();
            return map(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get Drive file metadata: " + fileId, e);
        }
    }

    public byte[] downloadFileBytes(String fileId) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            driveClient.files().get(fileId).executeMediaAndDownloadTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download Drive file: " + fileId, e);
        }
    }

    public byte[] downloadGoogleThumbnailBytes(String fileId) {
        DriveFileInfo fileInfo = getFileInfo(fileId);
        if (!hasText(fileInfo.getThumbnailLink())) {
            return createThumbnailBytes(fileId, 350);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            driveClient.getRequestFactory().buildGetRequest(new GenericUrl(fileInfo.getThumbnailLink()))
                    .execute().download(out);
            return out.toByteArray();
        } catch (IOException e) {
            return createThumbnailBytes(fileId, 350);
        }
    }

    public byte[] createThumbnailBytes(String fileId, int maxSize) {
        byte[] originalBytes = downloadFileBytes(fileId);
        try {
            BufferedImage orig = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (orig == null) throw new IllegalStateException("Unsupported image format: " + fileId);
            double scale = Math.min((double) maxSize / orig.getWidth(), (double) maxSize / orig.getHeight());
            if (scale > 1.0) scale = 1.0;
            int w = Math.max(1, (int) Math.round(orig.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(orig.getHeight() * scale));
            BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(orig, 0, 0, w, h, null);
            } finally { g.dispose(); }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(thumb, "jpg", out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create thumbnail: " + fileId, e);
        }
    }

    private List<DriveFileInfo> search(String query) {
        try {
            List<DriveFileInfo> result = new ArrayList<>();
            String nextPageToken = null;
            do {
                FileList fileList = driveClient.files().list().setQ(query).setFields(FILE_FIELDS)
                        .setPageSize(driveProperties.getPageSize()).setPageToken(nextPageToken)
                        .setSupportsAllDrives(false).setIncludeItemsFromAllDrives(false).execute();
                if (fileList.getFiles() != null) fileList.getFiles().forEach(f -> result.add(map(f)));
                nextPageToken = fileList.getNextPageToken();
            } while (nextPageToken != null);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to query Google Drive", e);
        }
    }

    private DriveFileInfo map(com.google.api.services.drive.model.File file) {
        String parentId = (file.getParents() != null && !file.getParents().isEmpty()) ? file.getParents().get(0) : null;
        return DriveFileInfo.builder()
                .id(file.getId()).name(file.getName()).mimeType(file.getMimeType()).parentId(parentId)
                .createdTime(toInstant(file.getCreatedTime() != null ? file.getCreatedTime().getValue() : null))
                .modifiedTime(toInstant(file.getModifiedTime() != null ? file.getModifiedTime().getValue() : null))
                .trashed(Boolean.TRUE.equals(file.getTrashed()))
                .folder(Objects.equals(FOLDER_MIME_TYPE, file.getMimeType()))
                .thumbnailLink(file.getThumbnailLink()).build();
    }

    private Instant toInstant(Long value) { return value == null ? null : Instant.ofEpochMilli(value); }
    private boolean hasText(String v) { return v != null && !v.trim().isEmpty(); }
    private String escape(String v) { return v.replace("'", "\\'"); }
}
