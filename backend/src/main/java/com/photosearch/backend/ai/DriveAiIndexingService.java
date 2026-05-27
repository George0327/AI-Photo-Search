package com.photosearch.backend.ai;

import com.photosearch.backend.drive.DriveFileInfo;
import com.photosearch.backend.drive.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DriveAiIndexingService {

    private final GoogleDriveService googleDriveService;
    private final AiServiceClient aiServiceClient;

    public DriveIndexResult rebuildAlbumIndex(String albumId) {
        aiServiceClient.resetIndex();

        List<DriveFileInfo> images = googleDriveService.listImagesInAlbum(albumId);

        List<DriveIndexFailure> failures = new ArrayList<>();
        int indexedCount = indexImages(images, failures);

        return new DriveIndexResult(
                1,
                images.size(),
                indexedCount,
                failures.size(),
                failures
        );
    }

    public DriveIndexResult rebuildFullDriveIndex() {
        aiServiceClient.resetIndex();

        String rootId = googleDriveService.resolveRootFolderId();
        List<DriveFileInfo> albums = googleDriveService.listAlbumFolders(rootId);

        int totalImages = 0;
        int indexedCount = 0;
        List<DriveIndexFailure> failures = new ArrayList<>();

        for (DriveFileInfo album : albums) {
            List<DriveFileInfo> images = googleDriveService.listImagesInAlbum(album.getId());
            totalImages += images.size();

            indexedCount += indexImages(images, failures);
        }

        return new DriveIndexResult(
                albums.size(),
                totalImages,
                indexedCount,
                failures.size(),
                failures
        );
    }

    private int indexImages(List<DriveFileInfo> images, List<DriveIndexFailure> failures) {
        int indexedCount = 0;

        for (DriveFileInfo image : images) {
            if (!isSupportedDriveImage(image)) {
                failures.add(new DriveIndexFailure(
                        image.getId(),
                        image.getName(),
                        "Skipped: file is not a supported Drive image. MIME type: " + image.getMimeType()
                ));
                continue;
            }

            try {
                byte[] imageBytes = googleDriveService.downloadFileBytes(image.getId());

                AiIndexResponse response = aiServiceClient.indexImage(
                        image.getId(),
                        image.getName(),
                        image.getMimeType(),
                        imageBytes
                );

                if (response == null || !response.success()) {
                    failures.add(new DriveIndexFailure(
                            image.getId(),
                            image.getName(),
                            response != null ? response.message() : "No response from AI service"
                    ));
                    continue;
                }

                indexedCount++;
            } catch (Exception e) {
                failures.add(new DriveIndexFailure(
                        image.getId(),
                        image.getName(),
                        e.getMessage()
                ));
            }
        }

        return indexedCount;
    }

    private boolean isSupportedDriveImage(DriveFileInfo file) {
        return file.getMimeType() != null
                && file.getMimeType().startsWith("image/");
    }

    public record DriveIndexResult(
            int albumCount,
            int totalImages,
            int indexedCount,
            int failedCount,
            List<DriveIndexFailure> failures
    ) {
    }

    public record DriveIndexFailure(
            String fileId,
            String fileName,
            String error
    ) {
    }
}