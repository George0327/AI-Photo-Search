import {
  ActionIcon,
  Alert,
  Box,
  Button,
  Container,
  Group,
  Menu,
  Paper,
  ScrollArea,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { IconAlertCircle, IconHistory, IconLayoutSidebar, IconRefresh, IconTrash } from "@tabler/icons-react";
import { useCallback, useState } from "react";

import { AlbumSidebar } from "../components/AlbumSidebar";
import { PhotoGrid } from "../components/PhotoGrid";
import { PhotoPreviewModal } from "../components/PhotoPreviewModal";
import { SearchBar } from "../components/SearchBar";
import { useGallery } from "../hooks/useGallery";
import { SIDEBAR_WIDTH } from "../constants";
import type { DriveFileInfo } from "../api/driveApi";

export default function DriveGalleryPage() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const isDesktop = useMediaQuery("(min-width: 768px)") ?? true;

  const {
    paginatedImages,
    visibleImages,
    previewImage,
    searchScores,
    error,
    imagesLoading,
    searchLoading,
    isSearchActive,
    inputMode,
    searchQuery,
    topK,
    referenceFiles,
    withinResults,
    savedSearches,
    activePage,
    totalPages,
    firstVisible,
    lastVisible,
    searchLabel,
    headerTitle,
    headerSubtitle,
    selectedAlbum,
    albumSidebarProps,
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
  } = useGallery();

  // Stable wrapper so the memoized AlbumRow components don't re-render on every gallery state change.
  const { onSelectAlbum } = albumSidebarProps;
  const handleSelectAlbum = useCallback(
    (album: DriveFileInfo) => {
      onSelectAlbum(album);
      if (!isDesktop) setSidebarOpen(false);
    },
    [onSelectAlbum, isDesktop],
  );

  return (
    <>
      {/* Fixed sidebar panel — slides in/out */}
      <Box
        style={{
          position: "fixed",
          left: 0,
          top: 64,
          bottom: 0,
          width: SIDEBAR_WIDTH,
          transform: sidebarOpen ? "translateX(0)" : `translateX(-${SIDEBAR_WIDTH}px)`,
          transition: "transform 0.25s cubic-bezier(0.4, 0, 0.2, 1)",
          zIndex: 100,
          borderRight: "1px solid var(--mantine-color-default-border)",
          background: "var(--mantine-color-body)",
          overflow: "hidden",
        }}
      >
        <AlbumSidebar {...albumSidebarProps} onSelectAlbum={handleSelectAlbum} />
      </Box>

      {/* Mobile backdrop — closes sidebar when tapped */}
      {sidebarOpen && !isDesktop && (
        <Box
          onClick={() => setSidebarOpen(false)}
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0, 0, 0, 0.4)",
            zIndex: 99,
          }}
        />
      )}

      {/* Main content — shifts right when sidebar is open on desktop */}
      <Box
        style={{
          marginLeft: sidebarOpen && isDesktop ? SIDEBAR_WIDTH : 0,
          transition: "margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1)",
        }}
      >
        <Container fluid py="lg" px="xl">
          {error && (
            <Alert color="red" title="Error" icon={<IconAlertCircle size={18} />} radius="md" mb="lg">
              {error}
            </Alert>
          )}

          <Stack>
            <Paper withBorder radius="md" p="lg">
              <Stack gap="md">
                <Group justify="space-between">
                  <Group>
                    <ActionIcon
                      variant="default"
                      size="lg"
                      aria-label="Toggle sidebar"
                      onClick={() => setSidebarOpen((o) => !o)}
                    >
                      <IconLayoutSidebar size={18} />
                    </ActionIcon>
                    <div>
                      <Title order={3}>{headerTitle}</Title>
                      <Text size="sm" c="dimmed">{headerSubtitle}</Text>
                    </div>
                  </Group>
                  <Group gap="xs">
                    {savedSearches.length > 0 && (
                      <Menu shadow="md" width={320} position="bottom-end">
                        <Menu.Target>
                          <Button variant="light" size="sm" color="gray"
                                  leftSection={<IconHistory size={16} />}>
                            Recent searches
                          </Button>
                        </Menu.Target>
                        <Menu.Dropdown>
                          <Menu.Label>Recent searches</Menu.Label>
                          <ScrollArea.Autosize mah={320}>
                            {savedSearches.map((s) => (
                              <Menu.Item key={s.id} onClick={() => recallSearch(s.id)}>
                                <Text size="sm" truncate>
                                  {s.searchType === "FACE"
                                    ? "Face search"
                                    : `"${s.queryText ?? ""}"`}
                                </Text>
                                <Text size="xs" c="dimmed">{s.resultCount} results</Text>
                              </Menu.Item>
                            ))}
                          </ScrollArea.Autosize>
                          <Menu.Divider />
                          <Menu.Item
                            color="red"
                            leftSection={<IconTrash size={16} />}
                            onClick={clearRecentSearches}
                          >
                            Clear recent searches
                          </Menu.Item>
                        </Menu.Dropdown>
                      </Menu>
                    )}
                    {selectedAlbum && !isSearchActive && (
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
                </Group>

                <SearchBar
                  mode={inputMode}
                  value={searchQuery}
                  topK={topK}
                  loading={searchLoading}
                  searchActive={isSearchActive}
                  referenceFiles={referenceFiles}
                  withinResults={withinResults}
                  onChange={setSearchQuery}
                  onModeChange={handleModeChange}
                  onTopKChange={setTopK}
                  onReferenceFilesChange={setReferenceFiles}
                  onWithinResultsChange={setWithinResults}
                  onSearch={handleSearch}
                  onClear={clearSearch}
                />
              </Stack>
            </Paper>

            <PhotoGrid
              images={paginatedImages}
              loading={imagesLoading || searchLoading}
              searchMode={isSearchActive}
              searchLabel={searchLabel}
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
        </Container>
      </Box>

      <PhotoPreviewModal image={previewImage} onClose={() => setPreviewImage(null)} />
    </>
  );
}
