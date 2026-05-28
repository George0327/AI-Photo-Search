import { useEffect, useMemo, useState } from "react";
import {
  ActionIcon,
  Alert,
  Box,
  Button,
  Container,
  Drawer,
  Flex,
  Group,
  Paper,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconMenu2,
  IconRefresh,
} from "@tabler/icons-react";

import type { DriveFileInfo } from "../api/driveApi";
import { fetchAlbums, fetchImages } from "../api/driveApi";
import { searchPhotos } from "../api/searchApi";
import type { PhotoSearchResult } from "../api/searchApi";

import { AlbumSidebar } from "../components/AlbumSidebar";
import { SearchBar } from "../components/SearchBar";
import { PhotoGrid } from "../components/PhotoGrid";
import { PhotoPreviewModal } from "../components/PhotoPreviewModal";

const PHOTOS_PER_PAGE = 20;

export default function DriveGalleryPage() {
  const [albums, setAlbums] = useState<DriveFileInfo[]>([]);
  const [selectedAlbum, setSelectedAlbum] = useState<DriveFileInfo | null>(null);
  const [selectedSearchAlbumIds, setSelectedSearchAlbumIds] = useState<string[]>([]);

  const [images, setImages] = useState<DriveFileInfo[]>([]);
  const [previewImage, setPreviewImage] = useState<DriveFileInfo | null>(null);

  const [activePage, setActivePage] = useState(1);

  const [searchQuery, setSearchQuery] = useState("");
  const [lastSearchQuery, setLastSearchQuery] = useState("");
  const [topK, setTopK] = useState("20");
  const [searchResults, setSearchResults] = useState<PhotoSearchResult[]>([]);
  const [searchMode, setSearchMode] = useState(false);

  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  const [albumsLoading, setAlbumsLoading] = useState(false);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const visibleImages = useMemo(() => {
    if (searchMode) return searchResults.map((r) => r.file);
    return images;
  }, [searchMode, searchResults, images]);

  const searchScores = useMemo(() => {
    if (!searchMode) return undefined;
    return Object.fromEntries(searchResults.map((r) => [r.file.id, r.score]));
  }, [searchMode, searchResults]);

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

  async function handleSearch() {
    const trimmed = searchQuery.trim();
    if (!trimmed) return;
    if (selectedSearchAlbumIds.length === 0) {
      setError("Select at least one album to search.");
      return;
    }
    try {
      setError(null);
      setSearchLoading(true);
      setActivePage(1);
      const data = await searchPhotos(trimmed, Number(topK), selectedSearchAlbumIds);
      setSearchResults(data.results);
      setSearchMode(true);
      setLastSearchQuery(trimmed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed.");
    } finally {
      setSearchLoading(false);
    }
  }

  function clearSearch() {
    setSearchMode(false);
    setSearchResults([]);
    setSearchQuery("");
    setLastSearchQuery("");
    setActivePage(1);
  }

  useEffect(() => {
    loadAlbums();
  }, []);

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
      setMobileSidebarOpen(false);
    },
    onSearchAlbumToggle: (albumId: string, checked: boolean) =>
      setSelectedSearchAlbumIds((curr) =>
        checked ? [...curr, albumId] : curr.filter((id) => id !== albumId),
      ),
    onSelectAllForSearch: () =>
      setSelectedSearchAlbumIds(albums.map((a) => a.id)),
    onClearSearchSelection: () => setSelectedSearchAlbumIds([]),
  };

  return (
    <>
      <Container fluid py="lg" px="xl">
        {error && (
          <Alert
            color="red"
            title="Error"
            icon={<IconAlertCircle size={18} />}
            radius="md"
            mb="lg"
          >
            {error}
          </Alert>
        )}

        <Flex gap="lg" align="flex-start">
          <Box w={300} visibleFrom="md" style={{ position: "sticky", top: 80 }}>
            <AlbumSidebar {...albumSidebarProps} />
          </Box>

          <Stack style={{ flex: 1, minWidth: 0 }}>
            <Paper withBorder radius="md" p="lg">
              <Stack gap="md">
                <Group justify="space-between">
                  <Group>
                    <ActionIcon
                      hiddenFrom="md"
                      variant="default"
                      size="lg"
                      aria-label="Open albums menu"
                      onClick={() => setMobileSidebarOpen(true)}
                    >
                      <IconMenu2 size={18} />
                    </ActionIcon>
                    <div>
                      <Title order={3}>
                        {searchMode
                          ? `Results for "${lastSearchQuery}"`
                          : selectedAlbum?.name || "Select an album"}
                      </Title>
                      <Text size="sm" c="dimmed">
                        {searchMode
                          ? `${searchResults.length} indexed photos found`
                          : `${images.length} photos in gallery`}
                      </Text>
                    </div>
                  </Group>
                  {selectedAlbum && !searchMode && (
                    <Button
                      variant="light"
                      size="sm"
                      leftSection={<IconRefresh size={16} />}
                      onClick={() => loadImages(selectedAlbum.id)}
                      loading={imagesLoading}
                    >
                      Refresh
                    </Button>
                  )}
                </Group>

                <SearchBar
                  value={searchQuery}
                  topK={topK}
                  loading={searchLoading}
                  searchMode={searchMode}
                  onChange={setSearchQuery}
                  onTopKChange={setTopK}
                  onSearch={handleSearch}
                  onClear={clearSearch}
                />
              </Stack>
            </Paper>

            <PhotoGrid
              images={paginatedImages}
              loading={imagesLoading || searchLoading}
              searchMode={searchMode}
              activePage={activePage}
              totalPages={totalPages}
              totalCount={visibleImages.length}
              firstVisible={firstVisible}
              lastVisible={lastVisible}
              scores={searchScores}
              onPageChange={setActivePage}
              onImageClick={setPreviewImage}
            />
          </Stack>
        </Flex>
      </Container>

      <Drawer
        opened={mobileSidebarOpen}
        onClose={() => setMobileSidebarOpen(false)}
        title="Gallery Albums"
        padding="md"
        size="sm"
      >
        <AlbumSidebar {...albumSidebarProps} />
      </Drawer>

      <PhotoPreviewModal
        image={previewImage}
        onClose={() => setPreviewImage(null)}
      />
    </>
  );
}
