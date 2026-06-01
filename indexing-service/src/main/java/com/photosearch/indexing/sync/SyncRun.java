package com.photosearch.indexing.sync;

import com.photosearch.indexing.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sync_runs", indexes = {
        @Index(name = "idx_sync_runs_status", columnList = "status"),
        @Index(name = "idx_sync_runs_type", columnList = "type"),
        @Index(name = "idx_sync_runs_started_at", columnList = "started_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncRun {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private SyncType type;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) @Builder.Default
    private SyncStatus status = SyncStatus.RUNNING;

    @Column(name = "target_album_drive_id", length = 255) private String targetAlbumDriveId;
    @Column(name = "target_album_title", length = 500) private String targetAlbumTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by_user_id")
    @ToString.Exclude
    private User startedBy;

    @Column(name = "started_at", nullable = false) private Instant startedAt;
    private Instant finishedAt;

    @Column(nullable = false) @Builder.Default private int albumsScanned = 0;
    @Column(nullable = false) @Builder.Default private int mediaSeen = 0;
    @Column(nullable = false) @Builder.Default private int mediaCreated = 0;
    @Column(nullable = false) @Builder.Default private int mediaUpdated = 0;
    @Column(nullable = false) @Builder.Default private int mediaMarkedDeleted = 0;
    @Column(nullable = false) @Builder.Default private int mediaIndexed = 0;
    @Column(nullable = false) @Builder.Default private int mediaFailed = 0;

    @Column(length = 4000) private String errorMessage;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist protected void onCreate() {
        Instant now = Instant.now();
        if (this.startedAt == null) this.startedAt = now;
        this.createdAt = now; this.updatedAt = now;
    }
    @PreUpdate protected void onUpdate() { this.updatedAt = Instant.now(); }

    public void markSuccess() { this.status = SyncStatus.SUCCESS; this.finishedAt = Instant.now(); }
    public void markPartial(String msg) { this.status = SyncStatus.PARTIAL; this.errorMessage = msg; this.finishedAt = Instant.now(); }
    public void markFailed(String msg) { this.status = SyncStatus.FAILED; this.errorMessage = msg; this.finishedAt = Instant.now(); }
}
