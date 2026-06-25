package com.photosearch.indexing.drive;

import com.photosearch.indexing.ai.indexing.AlbumSearchStatus;
import com.photosearch.indexing.ai.indexing.DriveAiIndexingService;
import com.photosearch.indexing.album.Album;
import com.photosearch.indexing.album.AlbumRepository;
import com.photosearch.indexing.media.MediaItem;
import com.photosearch.indexing.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/drive")
@RequiredArgsConstructor
public class DriveController {

    private final GoogleDriveService googleDriveService;
    private final DriveAiIndexingService driveAiIndexingService;
    private final AlbumRepository albumRepository;
    private final MediaItemRepository mediaItemRepository;

    @GetMapping("/albums")
    public List<DriveFileInfo> listAlbums() {
        String rootId = googleDriveService.resolveRootFolderId();
        return googleDriveService.listAlbumFolders(rootId);
    }

    /** Indexing summary for the gallery sidebar — available to every user, DB-only (no Drive calls). */
    @GetMapping("/albums/index-status")
    public List<AlbumSearchStatus> getAlbumSearchStatuses() {
        return driveAiIndexingService.getAlbumIndexStatuses().stream()
                .map(AlbumSearchStatus::from)
                .toList();
    }

    @GetMapping("/albums/{albumId}/images")
    public List<DriveFileInfo> listImages(@PathVariable String albumId) {
        List<DriveFileInfo> images = googleDriveService.listImagesInAlbum(albumId);
        // Prefix each photo's display name with the (sanitized) event/album name.
        albumRepository.findByDriveFolderId(albumId).ifPresent(album ->
                images.forEach(img -> img.setName(PhotoNaming.displayName(album.getTitle(), img.getName()))));
        return images;
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
        // Download as "EventName_originalFilename" when we know the album; fall back to the raw name.
        String downloadName = mediaItemRepository.findByDriveFileId(fileId)
                .map(item -> PhotoNaming.displayName(
                        Optional.ofNullable(item.getAlbum()).map(Album::getTitle).orElse(null),
                        item.getFilename()))
                .orElse(fileInfo.getName());
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(downloadName, StandardCharsets.UTF_8).build().toString())
                .body(imageBytes);
    }

    private MediaType parseImageMediaType(String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) return MediaType.parseMediaType(mimeType);
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
