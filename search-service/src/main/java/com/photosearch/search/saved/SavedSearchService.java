package com.photosearch.search.saved;

import com.photosearch.search.drive.DriveFileInfo;
import com.photosearch.search.drive.PhotoNaming;
import com.photosearch.search.media.MediaItem;
import com.photosearch.search.media.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persists each search (per user) and serves the saved-search history. Used to power
 * "search within results" and recall of past searches. Pruned to the most recent
 * {@code app.search.saved-history-limit} entries per user.
 */
@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private final SavedSearchRepository repository;
    private final MediaItemRepository mediaItemRepository;

    @Value("${app.search.saved-history-limit:20}")
    private int historyLimit;

    /** Records a completed search. No-op if there is no user or no results to remember. */
    @Transactional
    public void save(String searchType, String queryText, List<String> albumIds, List<String> resultFileIds) {
        String email = currentUserEmail();
        if (email == null || resultFileIds == null || resultFileIds.isEmpty()) return;

        repository.save(SavedSearch.builder()
                .userEmail(email)
                .searchType(searchType)
                .queryText(queryText)
                .albumIds(albumIds == null ? "" : String.join(",", albumIds))
                .resultFileIds(String.join(",", resultFileIds))
                .resultCount(resultFileIds.size())
                .createdAt(Instant.now())
                .build());

        List<SavedSearch> all = repository.findByUserEmailOrderByCreatedAtDesc(email);
        if (all.size() > historyLimit) {
            repository.deleteAll(all.subList(historyLimit, all.size()));
        }
    }

    public List<SavedSearchSummary> list() {
        return repository.findByUserEmailOrderByCreatedAtDesc(currentUserEmail()).stream()
                .map(SavedSearchSummary::from)
                .toList();
    }

    /** Deletes the current user's entire saved-search history. */
    @Transactional
    public void clearAll() {
        repository.deleteByUserEmail(currentUserEmail());
    }

    /** Re-hydrates a saved search's stored photo IDs (preserving ranked order) into displayable results. */
    public SavedSearchResults recall(Long id) {
        SavedSearch saved = repository.findByIdAndUserEmail(id, currentUserEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found."));

        List<String> ids = splitIds(saved.getResultFileIds());
        Map<String, MediaItem> byId = ids.isEmpty() ? Map.of() :
                mediaItemRepository.findActiveByDriveFileIdIn(ids).stream()
                        .collect(Collectors.toMap(MediaItem::getDriveFileId, Function.identity()));

        List<RecalledResult> results = new ArrayList<>();
        for (String fileId : ids) {                       // preserve the stored ranked order
            MediaItem item = byId.get(fileId);
            if (item == null) continue;                   // photo since deleted in Drive — skip
            results.add(new RecalledResult(item.getAlbum().getDriveFolderId(), driveFileInfoFrom(item)));
        }
        return new SavedSearchResults(saved.getId(), saved.getSearchType(), saved.getQueryText(), results);
    }

    private DriveFileInfo driveFileInfoFrom(MediaItem item) {
        return DriveFileInfo.builder()
                .id(item.getDriveFileId())
                .name(PhotoNaming.displayName(item.getAlbum().getTitle(), item.getFilename()))
                .mimeType(item.getMimeType())
                .parentId(item.getAlbum().getDriveFolderId()).thumbnailLink(item.getDriveThumbnailLink())
                .createdTime(item.getCreatedInDriveAt()).modifiedTime(item.getModifiedInDriveAt())
                .trashed(false).folder(false).build();
    }

    private static List<String> splitIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).filter(s -> !s.isBlank()).toList();
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public record SavedSearchSummary(Long id, String searchType, String queryText,
                                     List<String> albumIds, int resultCount, Instant createdAt) {
        static SavedSearchSummary from(SavedSearch s) {
            return new SavedSearchSummary(s.getId(), s.getSearchType(), s.getQueryText(),
                    splitIds(s.getAlbumIds()), s.getResultCount(), s.getCreatedAt());
        }
    }

    public record SavedSearchResults(Long id, String searchType, String queryText, List<RecalledResult> results) {}
    public record RecalledResult(String albumId, DriveFileInfo file) {}
}
