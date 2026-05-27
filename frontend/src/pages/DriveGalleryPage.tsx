import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  AppShell,
  Badge,
  Button,
  Card,
  Center,
  Group,
  Image,
  Loader,
  Modal,
  Pagination,
  ScrollArea,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconDownload,
  IconPhoto,
  IconRefresh,
  IconSearch,
  IconX,
} from "@tabler/icons-react";

import type { DriveFileInfo } from "../api/driveApi";
import {
  fetchAlbums,
  fetchImages,
  getImageContentUrl,
  getImageDownloadUrl,
  getImageThumbnailUrl,
} from "../api/driveApi";

import { searchPhotos } from "../api/searchApi";
import type { PhotoSearchResult } from "../api/searchApi";

const PHOTOS_PER_PAGE = 20;

export default function DriveGalleryPage() {
  const [albums, setAlbums] = useState<DriveFileInfo[]>([]);
  const [selectedAlbum, setSelectedAlbum] = useState<DriveFileInfo | null>(null);
  const [images, setImages] = useState<DriveFileInfo[]>([]);
  const [previewImage, setPreviewImage] = useState<DriveFileInfo | null>(null);

  const [activePage, setActivePage] = useState(1);

  const [searchQuery, setSearchQuery] = useState("");
  const [topK, setTopK] = useState("10");
  const [searchResults, setSearchResults] = useState<PhotoSearchResult[]>([]);
  const [searchMode, setSearchMode] = useState(false);

  const [albumsLoading, setAlbumsLoading] = useState(false);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const visibleImages = useMemo(() => {
    if (searchMode) {
      return searchResults.map((result) => result.file);
    }

    return images;
  }, [searchMode, searchResults, images]);

  const totalPages = Math.max(
    1,
    Math.ceil(visibleImages.length / PHOTOS_PER_PAGE)
  );

  const paginatedImages = useMemo(() => {
    const startIndex = (activePage - 1) * PHOTOS_PER_PAGE;
    const endIndex = startIndex + PHOTOS_PER_PAGE;

    return visibleImages.slice(startIndex, endIndex);
  }, [visibleImages, activePage]);

  const firstVisibleNumber =
    visibleImages.length === 0 ? 0 : (activePage - 1) * PHOTOS_PER_PAGE + 1;

  const lastVisibleNumber = Math.min(
    activePage * PHOTOS_PER_PAGE,
    visibleImages.length
  );

  async function loadAlbums() {
    try {
      setError(null);
      setAlbumsLoading(true);

      const data = await fetchAlbums();
      setAlbums(data);

      if (!selectedAlbum && data.length > 0) {
        setSelectedAlbum(data[0]);
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
    const trimmedQuery = searchQuery.trim();

    if (!trimmedQuery) {
      return;
    }

    try {
      setError(null);
      setSearchLoading(true);
      setActivePage(1);

      const data = await searchPhotos(trimmedQuery, Number(topK));
      setSearchResults(data.results);
      setSearchMode(true);
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

  return (
    <>
      <AppShell
        header={{ height: 56 }}
        navbar={{ width: 300, breakpoint: "sm" }}
        padding="md"
      >
        <AppShell.Navbar p="md">
          <Group justify="space-between" mb="md">
            <Title order={4}>Albums</Title>
            <Badge variant="light">{albums.length}</Badge>
          </Group>

          {albumsLoading ? (
            <Center py="xl">
              <Loader />
            </Center>
          ) : (
            <ScrollArea>
              <Stack gap="xs">
                {albums.map((album) => (
                  <Button
                    key={album.id}
                    variant={
                      selectedAlbum?.id === album.id ? "filled" : "subtle"
                    }
                    justify="flex-start"
                    leftSection={<IconPhoto size={16} />}
                    onClick={() => {
                      clearSearch();
                      setSelectedAlbum(album);
                    }}
                  >
                    {album.name}
                  </Button>
                ))}

                {albums.length === 0 && (
                  <Text size="sm" c="dimmed">
                    No albums found.
                  </Text>
                )}
              </Stack>
            </ScrollArea>
          )}
        </AppShell.Navbar>

        <AppShell.Main>
          <Stack>
            <Group justify="space-between">
              <div>
                <Title order={2}>Gallery</Title>
                <Text size="sm" c="dimmed">
                  Browse Google Drive albums and search indexed photos.
                </Text>
              </div>

              <Button
                leftSection={<IconRefresh size={16} />}
                variant="light"
                onClick={loadAlbums}
                loading={albumsLoading}
              >
                Refresh albums
              </Button>
            </Group>
            {error && (
              <Alert
                color="red"
                title="Error"
                icon={<IconAlertCircle size={18} />}
              >
                {error}
              </Alert>
            )}

            <Group justify="space-between" align="flex-start">
              <div>
                <Title order={2}>
                  {searchMode
                    ? `Search results for "${searchQuery}"`
                    : selectedAlbum
                      ? selectedAlbum.name
                      : "Select an album"}
                </Title>

                <Text size="sm" c="dimmed">
                  {searchMode
                    ? `${searchResults.length} indexed result${
                        searchResults.length === 1 ? "" : "s"
                      }`
                    : `${images.length} image${
                        images.length === 1 ? "" : "s"
                      }${
                        images.length > PHOTOS_PER_PAGE
                          ? ` · ${PHOTOS_PER_PAGE} per page`
                          : ""
                      }`}
                </Text>
              </div>

              {selectedAlbum && !searchMode && (
                <Button
                  leftSection={<IconRefresh size={16} />}
                  variant="light"
                  onClick={() => loadImages(selectedAlbum.id)}
                  loading={imagesLoading}
                >
                  Refresh photos
                </Button>
              )}
            </Group>

            <Stack gap="xs">
              <Group align="end">
                <TextInput
                  label="Search indexed photos"
                  placeholder='Example: "dog", "mountain", "people at a table"'
                  value={searchQuery}
                  onChange={(event) =>
                    setSearchQuery(event.currentTarget.value)
                  }
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      handleSearch();
                    }
                  }}
                  style={{ flex: 1 }}
                />

                <Select
                  label="Results"
                  value={topK}
                  onChange={(value) => setTopK(value ?? "10")}
                  data={[
                    { value: "5", label: "Top 5" },
                    { value: "10", label: "Top 10" },
                    { value: "20", label: "Top 20" },
                    { value: "30", label: "Top 30" },
                    { value: "50", label: "Top 50" },
                    { value: "100", label: "Top 100" },
                  ]}
                  w={110}
                />

                <Button
                  leftSection={<IconSearch size={16} />}
                  onClick={handleSearch}
                  loading={searchLoading}
                >
                  Search
                </Button>

                {searchMode && (
                  <Button
                    variant="light"
                    leftSection={<IconX size={16} />}
                    onClick={clearSearch}
                  >
                    Clear
                  </Button>
                )}
              </Group>

              <Text size="xs" c="dimmed">
                Search only returns photos that were indexed by an admin.
              </Text>
            </Stack>

            {imagesLoading || searchLoading ? (
              <Center py={80}>
                <Loader />
              </Center>
            ) : (
              <>
                <SimpleGrid
                  cols={{ base: 1, sm: 2, md: 3, lg: 4 }}
                  spacing="md"
                >
                  {paginatedImages.map((image) => (
                    <Card
                      key={image.id}
                      shadow="sm"
                      padding="sm"
                      radius="md"
                      withBorder
                      style={{ cursor: "pointer" }}
                      onClick={() => setPreviewImage(image)}
                    >
                      <Card.Section>
                        <Image
                          src={getImageThumbnailUrl(image.id)}
                          alt={image.name}
                          h={220}
                          fit="cover"
                          loading="lazy"
                        />
                      </Card.Section>

                      <Text size="sm" mt="sm" fw={500} truncate="end">
                        {image.name}
                      </Text>

                      <Text size="xs" c="dimmed">
                        {image.mimeType}
                      </Text>
                    </Card>
                  ))}
                </SimpleGrid>

                {visibleImages.length > PHOTOS_PER_PAGE && (
                  <Group justify="space-between" mt="md">
                    <Text size="sm" c="dimmed">
                      Showing {firstVisibleNumber}–{lastVisibleNumber} of{" "}
                      {visibleImages.length}{" "}
                      {searchMode ? "results" : "photos"}
                    </Text>

                    <Pagination
                      total={totalPages}
                      value={activePage}
                      onChange={setActivePage}
                      siblings={1}
                      boundaries={1}
                    />
                  </Group>
                )}
              </>
            )}

            {!imagesLoading && !searchLoading && visibleImages.length === 0 && (
              <Center py={80}>
                <Text c="dimmed">
                  {searchMode
                    ? "No indexed photos matched this search."
                    : "No images found in this album."}
                </Text>
              </Center>
            )}
          </Stack>
        </AppShell.Main>
      </AppShell>

      <Modal
        opened={previewImage !== null}
        onClose={() => setPreviewImage(null)}
        title={previewImage?.name}
        size="xl"
        centered
        transitionProps={{ duration: 100 }}
      >
        {previewImage && (
          <Stack>
            <Group justify="flex-end">
              <Button
                component="a"
                href={getImageDownloadUrl(previewImage.id)}
                download={previewImage.name}
                leftSection={<IconDownload size={16} />}
              >
                Download
              </Button>
            </Group>

            <Image
              src={getImageContentUrl(previewImage.id)}
              alt={previewImage.name}
              fit="contain"
              mah="75vh"
            />
          </Stack>
        )}
      </Modal>
    </>
  );
}