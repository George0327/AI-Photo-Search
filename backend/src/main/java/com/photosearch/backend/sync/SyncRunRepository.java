package com.photosearch.backend.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {
    List<SyncRun> findAllByOrderByStartedAtDesc();
    List<SyncRun> findByStatusOrderByStartedAtDesc(SyncStatus status);
}