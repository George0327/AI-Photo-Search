package com.photosearch.search.saved;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search/saved")
@RequiredArgsConstructor
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    @GetMapping
    public List<SavedSearchService.SavedSearchSummary> list() {
        return savedSearchService.list();
    }

    @GetMapping("/{id}")
    public SavedSearchService.SavedSearchResults recall(@PathVariable Long id) {
        return savedSearchService.recall(id);
    }

    @DeleteMapping
    public void clear() {
        savedSearchService.clearAll();
    }
}
