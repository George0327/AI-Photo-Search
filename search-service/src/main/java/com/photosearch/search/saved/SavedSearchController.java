package com.photosearch.search.saved;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search/saved")
@RequiredArgsConstructor
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    /** The current user's recent searches (most recent first). */
    @GetMapping
    public List<SavedSearchService.SavedSearchSummary> list() {
        return savedSearchService.list();
    }

    /** Re-open a past search: returns its photos (ranked order preserved). */
    @GetMapping("/{id}")
    public SavedSearchService.SavedSearchResults recall(@PathVariable Long id) {
        return savedSearchService.recall(id);
    }

    /** Clears the current user's entire saved-search history. */
    @DeleteMapping
    public void clear() {
        savedSearchService.clearAll();
    }
}
