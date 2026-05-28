package com.photosearch.backend.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findTop20ByOrderByStartedAtDesc();

    List<SyncRun> findTop20ByTypeOrderByStartedAtDesc(SyncType type);

    List<SyncRun> findByStatus(SyncStatus status);
}