import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { AlbumSearchStatus, DriveFileInfo } from "../api/driveApi";
import { fetchAlbums, fetchAlbumSearchStatuses, fetchImages } from "../api/driveApi";
import { searchPhotos } from "../api/searchApi";
import { searchByFace } from "../api/faceSearchApi";
import { clearSavedSearches, fetchSavedSearches, recallSavedSearch } from "../api/savedSearchApi";
import type { SavedSearchSummary } from "../api/savedSearchApi";
import type { SearchMode } from "../components/SearchBar";
import { MAX_TOP_K, MIN_TOP_K, PHOTOS_PER_PAGE } from "../constants";

export type SearchResult = { score: number; albumId: string; file: DriveFileInfo };

const SEARCH_LABELS: Record<SearchMode, string> = {
  text: "AI search results",
  face: "Face search results",
};

function clampTopK(value: string): number {
  const parsed = Number(value);
  if (Number.isNaN(parsed)) return 10;
  return Math.min(Math.max(Math.round(parsed), MIN_TOP_K), MAX_TOP_K);
}

export function useGallery() {
  // Text searches are reflected in the URL (?q=…&topK=…&albums=…) so results
  // can be bookmarked, shared, and survive a page refresh.
  const [searchParams, setSearchParams] = useSearchParams();
  const initialQuery = searchParams.get("q") ?? "";
  const initialTopK = searchParams.get("topK") ?? "10";
  const initialAlbumIds = useMemo(
    () => searchParams.get("albums")?.split(",").filter(Boolean) ?? [],
    // Read once on mount; afterwards the URL follows the state, not the other way.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const [albums, setAlbums] = useState<DriveFileInfo[]>([]);
  const [albumStatuses, setAlbumStatuses] = useState<Record<string, AlbumSearchStatus>>({});
  const [selectedAlbum, setSelectedAlbum] = useState<DriveFileInfo | null>(null);
  const [selectedSearchAlbumIds, setSelectedSearchAlbumIds] = useState<string[]>(initialAlbumIds);

  const [images, setImages] = useState<DriveFileInfo[]>([]);
  const [previewImage, setPreviewImage] = useState<DriveFileInfo | null>(null);

  const [activePage, setActivePage] = useState(1);

  const [inputMode, setInputMode] = useState<SearchMode>("text");
  const [searchQuery, setSearchQuery] = useState(initialQuery);
  const [topK, setTopK] = useState(initialTopK);
  const [referenceFiles, setReferenceFiles] = useState<File[]>([]);

  const [activeSearchType, setActiveSearchType] = useState<SearchMode | null>(null);
  const [lastSearchQuery, setLastSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);

  // "Search within results": the next search is restricted to the current result set.
  const [withinResults, setWithinResults] = useState(false);
  const [savedSearches, setSavedSearches] = useState<SavedSearchSummary[]>([]);

  const [albumsLoading, setAlbumsLoading] = useState(false);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // True until the search restored from the URL (if any) has been re-run.
  const pendingUrlSearch = useRef(initialQuery.trim().length > 0);

  const isSearchActive = activeSearchType !== null;

  const visibleImages = useMemo(() => {
    if (isSearchActive) return searchResults.map((r) => r.file);
    return images;
  }, [isSearchActive, searchResults, images]);

  const searchScores = useMemo(() => {
    if (!isSearchActive) return undefined;
    return Object.fromEntries(searchResults.map((r) => [r.file.id, r.score]));
  }, [isSearchActive, searchResults]);

  const totalPages = Math.max(1, Math.ceil(visibleImages.length / PHOTOS_PER_PAGE));

  const paginatedImages = useMemo(() => {
    const start = (activePage - 1) * PHOTOS_PER_PAGE;
    return visibleImages.slice(start, start + PHOTOS_PER_PAGE);
  }, [visibleImages, activePage]);

  const firstVisible = visibleImages.length === 0 ? 0 : (activePage - 1) * PHOTOS_PER_PAGE + 1;
  const lastVisible = Math.min(activePage * PHOTOS_PER_PAGE, visibleImages.length);

  async function loadAlbums() {
    try {
      setError(null);
      setAlbumsLoading(true);
      const data = await fetchAlbums();
      setAlbums(data);
      if (!selectedAlbum && data.length > 0) {
        setSelectedAlbum(data[0]);
        setSelectedSearchAlbumIds((curr) => (curr.length > 0 ? curr : [data[0].id]));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load albums.");
    } finally {
      setAlbumsLoading(false);
    }
    // Status badges are best-effort decoration: failure must not break the gallery.
    try {
      const statuses = await fetchAlbumSearchStatuses();
      setAlbumStatuses(Object.fromEntries(statuses.map((s) => [s.driveAlbumId, s])));
    } catch (err) {
      console.warn("Failed to load album index statuses:", err);
    }
  }

  const loadImages = useCallback(async (albumId: string) => {
    try {
      setError(null);
      setImagesLoading(true);
      setActivePage(1);
      const data = await fetchImages(albumId);
      setImages(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load images.");
    } finally {
      setImagesLoading(false);
    }
  }, []);

  const clearSearch = useCallback(() => {
    setActiveSearchType(null);
    setSearchResults([]);
    setSearchQuery("");
    setLastSearchQuery("");
    setReferenceFiles([]);
    setWithinResults(false);
    setActivePage(1);
    setSearchParams({}, { replace: true });
  }, [setSearchParams]);

  async function loadSavedSearches() {
    try {
      setSavedSearches(await fetchSavedSearches());
    } catch (err) {
      console.warn("Failed to load saved searches:", err);
    }
  }

  async function clearRecentSearches() {
    try {
      await clearSavedSearches();
      setSavedSearches([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to clear recent searches.");
    }
  }

  // Re-open a past search: load its (ranked) photos as the active result set.
  async function recallSearch(id: number) {
    try {
      setError(null);
      setSearchLoading(true);
      setActivePage(1);
      setWithinResults(false);
      const data = await recallSavedSearch(id);
      setSearchResults(data.results.map((r) => ({ score: 0, albumId: r.albumId, file: r.file })));
      setActiveSearchType(data.searchType === "FACE" ? "face" : "text");
      setLastSearchQuery(data.queryText ? data.queryText : "recalled search");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open saved search.");
    } finally {
      setSearchLoading(false);
    }
  }

  function handleModeChange(mode: SearchMode) {
    setInputMode(mode);
    // Normally switching modality starts fresh. But when "search within results"
    // is on, keep the current results so the user can search the OTHER modality
    // (text vs face) within them.
    if (isSearchActive && !withinResults) clearSearch();
  }

  async function runTextSearch(query: string, albumIds: string[], restrictToIds?: string[]) {
    const effectiveTopK = clampTopK(topK);
    try {
      setError(null);
      setSearchLoading(true);
      setActivePage(1);
      const data = await searchPhotos(query, effectiveTopK, albumIds, restrictToIds);
      setSearchResults(data.results);
      setActiveSearchType("text");
      setLastSearchQuery(restrictToIds ? `${query} (within results)` : query);
      // Only plain searches are reflected in the URL; a refine can't be bookmarked.
      if (!restrictToIds) {
        setSearchParams(
          { q: query, topK: String(effectiveTopK), albums: albumIds.join(",") },
          { replace: true },
        );
      }
      loadSavedSearches();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed.");
    } finally {
      setSearchLoading(false);
    }
  }

  async function handleSearch() {
    // "Search within results": restrict to the current results and scope to their albums.
    const refine = withinResults && isSearchActive && searchResults.length > 0;
    const restrictToIds = refine ? searchResults.map((r) => r.file.id) : undefined;
    const effectiveAlbumIds = refine
      ? [...new Set(searchResults.map((r) => r.albumId))]
      : selectedSearchAlbumIds;

    if (!refine && selectedSearchAlbumIds.length === 0) {
      setError("Select at least one album to search.");
      return;
    }

    if (inputMode === "text") {
      const trimmed = searchQuery.trim();
      if (!trimmed) return;
      await runTextSearch(trimmed, effectiveAlbumIds, restrictToIds);
      return;
    }

    if (referenceFiles.length === 0) {
      setError("Upload at least one reference photo first.");
      return;
    }

    try {
      setError(null);
      setSearchLoading(true);
      setActivePage(1);

      const data = await searchByFace(referenceFiles, effectiveAlbumIds, clampTopK(topK), restrictToIds);
      setSearchResults(data.results);
      setActiveSearchType("face");
      setLastSearchQuery(`${referenceFiles.length} reference photo${referenceFiles.length > 1 ? "s" : ""}${refine ? " (within results)" : ""}`);
      loadSavedSearches();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed.");
    } finally {
      setSearchLoading(false);
    }
  }

  function headerTitle() {
    if (!isSearchActive) return selectedAlbum?.name || "Select an album";
    if (activeSearchType === "text") return `Results for "${lastSearchQuery}"`;
    return `Face search — ${lastSearchQuery}`;
  }

  function headerSubtitle() {
    if (!isSearchActive) return `${images.length} photos in gallery`;
    return `${searchResults.length} matching photos found`;
  }

  useEffect(() => { loadAlbums(); loadSavedSearches(); }, []);

  useEffect(() => {
    if (selectedAlbum) {
      setActivePage(1);
      loadImages(selectedAlbum.id);
    }
  }, [selectedAlbum, loadImages]);

  // Re-run a text search restored from the URL once albums are available.
  useEffect(() => {
    if (!pendingUrlSearch.current || albums.length === 0) return;
    pendingUrlSearch.current = false;
    const query = searchQuery.trim();
    const albumIds = selectedSearchAlbumIds.length > 0
      ? selectedSearchAlbumIds
      : albums.map((a) => a.id);
    if (query) runTextSearch(query, albumIds);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [albums]);

  const onSelectAlbum = useCallback((album: DriveFileInfo) => {
    clearSearch();
    setSelectedAlbum(album);
  }, [clearSearch]);

  const onSearchAlbumToggle = useCallback((albumId: string, checked: boolean) =>
    setSelectedSearchAlbumIds((curr) =>
      checked ? [...curr, albumId] : curr.filter((id) => id !== albumId),
    ), []);

  const onSelectAllForSearch = useCallback(
    () => setSelectedSearchAlbumIds(albums.map((a) => a.id)), [albums]);

  const onClearSearchSelection = useCallback(() => setSelectedSearchAlbumIds([]), []);

  const albumSidebarProps = {
    albums,
    albumStatuses,
    albumsLoading,
    selectedAlbum,
    selectedSearchAlbumIds,
    onSelectAlbum,
    onSearchAlbumToggle,
    onSelectAllForSearch,
    onClearSearchSelection,
  };

  return {
    // data
    paginatedImages,
    visibleImages,
    previewImage,
    searchScores,
    error,
    // loading flags
    imagesLoading,
    searchLoading,
    // search state
    isSearchActive,
    activeSearchType,
    inputMode,
    searchQuery,
    topK,
    referenceFiles,
    withinResults,
    savedSearches,
    // pagination
    activePage,
    totalPages,
    firstVisible,
    lastVisible,
    // derived
    searchLabel: activeSearchType ? SEARCH_LABELS[activeSearchType] : undefined,
    headerTitle: headerTitle(),
    headerSubtitle: headerSubtitle(),
    selectedAlbum,
    albumSidebarProps,
    // actions
    setActivePage,
    setPreviewImage,
    setSearchQuery,
    setTopK,
    setReferenceFiles,
    setWithinResults,
    handleSearch,
    handleModeChange,
    clearSearch,
    recallSearch,
    clearRecentSearches,
    loadImages,
  };
}
