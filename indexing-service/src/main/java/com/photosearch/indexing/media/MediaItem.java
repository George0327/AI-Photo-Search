package com.photosearch.indexing.media;

import com.photosearch.indexing.album.Album;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "media_items", indexes = {
        @Index(name = "idx_media_drive_file_id", columnList = "drive_file_id"),
        @Index(name = "idx_media_album_id", columnList = "album_id"),
        @Index(name = "idx_media_deleted_in_drive", columnList = "deleted_in_drive"),
        @Index(name = "idx_media_indexing_status", columnList = "indexing_status"),
        @Index(name = "idx_media_face_indexing_status", columnList = "face_indexing_status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MediaItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drive_file_id", nullable = false, unique = true, length = 255)
    private String driveFileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    @ToString.Exclude
    private Album album;

    @Column(nullable = false, length = 500) private String filename;
    @Column(nullable = false, length = 100) private String mimeType;
    private Long sizeBytes;
    @Column(length = 1000) private String driveThumbnailLink;
    private Instant createdInDriveAt;
    private Instant modifiedInDriveAt;
    private Instant lastSeenInDriveAt;

    @Column(nullable = false) @Builder.Default
    private boolean deletedInDrive = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) @Builder.Default
    private IndexingStatus indexingStatus = IndexingStatus.NOT_INDEXED;

    private Instant faissIndexedAt;
    @Column(length = 2000) private String indexingError;

    // faces are tracked separately, mirroring the CLIP lifecycle above
    @Enumerated(EnumType.STRING)
    @Column(name = "face_indexing_status", nullable = false, length = 30) @Builder.Default
    private IndexingStatus faceIndexingStatus = IndexingStatus.NOT_INDEXED;

    private Instant faceIndexedAt;
    @Column(length = 2000) private String faceIndexingError;

    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist protected void onCreate() { Instant now = Instant.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = Instant.now(); }
}
