package com.photosearch.search.saved;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A persisted search (text or face) with its ranked result IDs, kept per user for refine and recall. Schema owned by indexing-service Flyway (V3); mapped read/write here. */
@Entity
@Table(name = "saved_searches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    /** "TEXT" or "FACE". */
    @Column(name = "search_type", nullable = false, length = 20)
    private String searchType;

    @Column(name = "query_text", length = 1000)
    private String queryText;

    /** Comma-joined album drive IDs the search was scoped to. */
    @Column(name = "album_ids", length = 2000)
    private String albumIds;

    /** Comma-joined drive file IDs of the results, in ranked order. */
    @Column(name = "result_file_ids", columnDefinition = "text", nullable = false)
    private String resultFileIds;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
