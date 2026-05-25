package com.photosearch.backend.drive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriveConnectionRepository extends JpaRepository<DriveConnection, Long> {

    Optional<DriveConnection> findFirstByActiveTrueOrderByIdDesc();

    List<DriveConnection> findAllByActiveTrue();
}