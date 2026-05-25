package com.photosearch.backend.drive;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "drive_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String accountEmail;

    @Column(length = 1000)
    private String scopes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    private Instant connectedAt;
    private Instant lastValidatedAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (connectedAt == null) {
            connectedAt = now;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}