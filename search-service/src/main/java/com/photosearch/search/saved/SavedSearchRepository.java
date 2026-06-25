package com.photosearch.search.saved;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    Optional<SavedSearch> findByIdAndUserEmail(Long id, String userEmail);
    void deleteByUserEmail(String userEmail);
}
