package com.photosearch.backend.sync;

import com.photosearch.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SyncStatus status = SyncStatus.RUNNING;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by_user_id")
    @ToString.Exclude
    private User startedBy;

    @Column(nullable = false)
    @Builder.Default
    private Integer newItemsCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer updatedItemsCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer deletedItemsCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer indexedItemsCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedItemsCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.startedAt = this.startedAt == null ? now : this.startedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
