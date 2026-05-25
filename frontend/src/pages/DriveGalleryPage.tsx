import { useEffect, useState } from "react";
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
  ScrollArea,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconPhoto, IconRefresh } from "@tabler/icons-react";
import type { DriveFileInfo } from "../api/driveApi";
import {
  fetchAlbums,
  fetchImages,
  getImageContentUrl,
  getImageThumbnailUrl,
} from "../api/driveApi";

export default function DriveGalleryPage() {
  const [albums, setAlbums] = useState<DriveFileInfo[]>([]);
  const [selectedAlbum, setSelectedAlbum] = useState<DriveFileInfo | null>(null);
  const [images, setImages] = useState<DriveFileInfo[]>([]);
  const [previewImage, setPreviewImage] = useState<DriveFileInfo | null>(null);

  const [albumsLoading, setAlbumsLoading] = useState(false);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

      const data = await fetchImages(albumId);
      setImages(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load images.");
    } finally {
      setImagesLoading(false);
    }
  }

  useEffect(() => {
    loadAlbums();
  }, []);

  useEffect(() => {
    if (selectedAlbum) {
      loadImages(selectedAlbum.id);
    }
  }, [selectedAlbum]);

  return (
    <>
      <AppShell
        header={{ height: 72 }}
        navbar={{ width: 300, breakpoint: "sm" }}
        padding="md"
      >
        <AppShell.Header>
          <Group h="100%" px="md" justify="space-between">
            <div>
              <Title order={3}>Photo AI Search</Title>
              <Text size="sm" c="dimmed">
                Google Drive gallery
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
        </AppShell.Header>

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
                    variant={selectedAlbum?.id === album.id ? "filled" : "subtle"}
                    justify="flex-start"
                    leftSection={<IconPhoto size={16} />}
                    onClick={() => setSelectedAlbum(album)}
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
            {error && (
              <Alert
                color="red"
                title="Error"
                icon={<IconAlertCircle size={18} />}
              >
                {error}
              </Alert>
            )}

            <Group justify="space-between">
              <div>
                <Title order={2}>
                  {selectedAlbum ? selectedAlbum.name : "Select an album"}
                </Title>
                <Text size="sm" c="dimmed">
                  {images.length} image{images.length === 1 ? "" : "s"}
                </Text>
              </div>

              {selectedAlbum && (
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

            {imagesLoading ? (
              <Center py={80}>
                <Loader />
              </Center>
            ) : (
              <SimpleGrid cols={{ base: 1, sm: 2, md: 3, lg: 4 }} spacing="md">
                {images.map((image) => (
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
            )}

            {!imagesLoading && selectedAlbum && images.length === 0 && (
              <Center py={80}>
                <Text c="dimmed">No images found in this album.</Text>
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
      >
        {previewImage && (
          <Image
            src={getImageContentUrl(previewImage.id)}
            alt={previewImage.name}
            fit="contain"
            mah="75vh"
          />
        )}
      </Modal>
    </>
  );
}