package com.photosearch.indexing.drive;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/drive")
@RequiredArgsConstructor
public class DriveController {

    private final GoogleDriveService googleDriveService;

    @GetMapping("/albums")
    public List<DriveFileInfo> listAlbums() {
        String rootId = googleDriveService.resolveRootFolderId();
        return googleDriveService.listAlbumFolders(rootId);
    }

    @GetMapping("/albums/{albumId}/images")
    public List<DriveFileInfo> listImages(@PathVariable String albumId) {
        return googleDriveService.listImagesInAlbum(albumId);
    }

    @GetMapping("/images/{fileId}/content")
    public ResponseEntity<byte[]> getImageContent(@PathVariable String fileId) {
        DriveFileInfo fileInfo = googleDriveService.getFileInfo(fileId);
        byte[] imageBytes = googleDriveService.downloadFileBytes(fileId);
        MediaType mediaType = parseImageMediaType(fileInfo.getMimeType());
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileInfo.getName(), StandardCharsets.UTF_8).build().toString())
                .body(imageBytes);
    }

    @GetMapping("/images/{fileId}/thumbnail")
    public ResponseEntity<byte[]> getImageThumbnail(@PathVariable String fileId) {
        DriveFileInfo fileInfo = googleDriveService.getFileInfo(fileId);
        if (fileInfo.getMimeType() == null || !fileInfo.getMimeType().startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }
        byte[] thumbnailBytes = googleDriveService.downloadGoogleThumbnailBytes(fileId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("thumbnail-" + fileInfo.getName(), StandardCharsets.UTF_8).build().toString())
                .body(thumbnailBytes);
    }

    @GetMapping("/images/{fileId}/download")
    public ResponseEntity<byte[]> downloadImage(@PathVariable String fileId) {
        DriveFileInfo fileInfo = googleDriveService.getFileInfo(fileId);
        if (fileInfo.getMimeType() == null || !fileInfo.getMimeType().startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }
        byte[] imageBytes = googleDriveService.downloadFileBytes(fileId);
        MediaType mediaType = parseImageMediaType(fileInfo.getMimeType());
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileInfo.getName(), StandardCharsets.UTF_8).build().toString())
                .body(imageBytes);
    }

    private MediaType parseImageMediaType(String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) return MediaType.parseMediaType(mimeType);
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
