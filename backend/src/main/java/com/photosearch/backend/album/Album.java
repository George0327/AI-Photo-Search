package com.photosearch.backend.album;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "albums",
        indexes = {
                @Index(name = "idx_albums_drive_folder_id", columnList = "drive_folder_id"),
                @Index(name = "idx_albums_enabled", columnList = "enabled")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drive_folder_id", nullable = false, unique = true, length = 255)
    private String driveFolderId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean deletedInDrive = false;

    private Instant lastSyncedAt;

    private Instant lastIndexedAt;

    private Instant faceIndexedAt;

    private Instant logoIndexedAt;

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