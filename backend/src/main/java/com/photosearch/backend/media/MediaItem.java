package com.photosearch.backend.media;

import com.photosearch.backend.album.Album;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "media_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String driveFileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    @ToString.Exclude
    private Album album;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(length = 1000)
    private String thumbnailPath;

    @Column(nullable = false)
    @Builder.Default
    private boolean indexedInFaiss = false;

    private Instant faissIndexedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean deletedInDrive = false;

    private Instant createdInDriveAt;

    private Instant modifiedInDriveAt;

    private Instant lastSeenAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
