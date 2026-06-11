import { useEffect, useMemo, useState } from "react";
import type { DriveFileInfo } from "../api/driveApi";
import { fetchAlbums, fetchImages } from "../api/driveApi";
import { searchPhotos } from "../api/searchApi";
import { searchByFace } from "../api/faceSearchApi";
import type { SearchMode } from "../components/SearchBar";

const PHOTOS_PER_PAGE = 20;

export type SearchResult = { score: number; albumId: string; file: DriveFileInfo };

const SEARCH_LABELS: Record<SearchMode, string> = {
  text: "AI search results",
  face: "Face search results",
};

export function useGallery() {
  const [albums, setAlbums] = useState<DriveFileInfo[]>([]);
  const [selectedAlbum, setSelectedAlbum] = useState<DriveFileInfo | null>(null);
  const [selectedSearchAlbumIds, setSelectedSearchAlbumIds] = useState<string[]>([]);

  const [images, setImages] = useState<DriveFileInfo[]>([]);
  const [previewImage, setPreviewImage] = useState<DriveFileInfo | null>(null);

  const [activePage, setActivePage] = useState(1);

  const [inputMode, setInputMode] = useState<SearchMode>("text");
  const [searchQuery, setSearchQuery] = useState("");
  const [topK, setTopK] = useState("10");
  const [referenceFiles, setReferenceFiles] = useState<File[]>([]);

  const [activeSearchType, setActiveSearchType] = useState<SearchMode | null>(null);
  const [lastSearchQuery, setLastSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);

  const [albumsLoading, setAlbumsLoading] = useState(false);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
        setSelectedSearchAlbumIds([data[0].id]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load albums.");
    } finally {
      setAlbumsLoading(false);
    }
  }

  async function loadImages(albumId: string) {
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
  }

  function clearSearch() {
    setActiveSearchType(null);
    setSearchResults([]);
    setSearchQuery("");
    setLastSearchQuery("");
    setReferenceFiles([]);
    setActivePage(1);
  }

  function handleModeChange(mode: SearchMode) {
    setInputMode(mode);
    if (isSearchActive) clearSearch();
  }

  async function handleSearch() {
    if (selectedSearchAlbumIds.length === 0) {
      setError("Select at least one album to search.");
      return;
    }

    if (inputMode === "text") {
      const trimmed = searchQuery.trim();
      if (!trimmed) return;
      try {
        setError(null);
        setSearchLoading(true);
        setActivePage(1);
        const data = await searchPhotos(trimmed, Number(topK), selectedSearchAlbumIds);
        setSearchResults(data.results);
        setActiveSearchType("text");
        setLastSearchQuery(trimmed);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Search failed.");
      } finally {
        setSearchLoading(false);
      }
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

      const data = await searchByFace(referenceFiles, selectedSearchAlbumIds, Number(topK));
      setSearchResults(data.results);
      setActiveSearchType("face");
      setLastSearchQuery(`${referenceFiles.length} reference photo${referenceFiles.length > 1 ? "s" : ""}`);
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

  useEffect(() => { loadAlbums(); }, []);

  useEffect(() => {
    if (selectedAlbum) {
      setActivePage(1);
      loadImages(selectedAlbum.id);
    }
  }, [selectedAlbum]);

  const albumSidebarProps = {
    albums,
    albumsLoading,
    selectedAlbum,
    selectedSearchAlbumIds,
    onSelectAlbum: (album: DriveFileInfo) => {
      clearSearch();
      setSelectedAlbum(album);
    },
    onSearchAlbumToggle: (albumId: string, checked: boolean) =>
      setSelectedSearchAlbumIds((curr) =>
        checked ? [...curr, albumId] : curr.filter((id) => id !== albumId),
      ),
    onSelectAllForSearch: () => setSelectedSearchAlbumIds(albums.map((a) => a.id)),
    onClearSearchSelection: () => setSelectedSearchAlbumIds([]),
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
    handleSearch,
    handleModeChange,
    clearSearch,
    loadImages,
  };
}
