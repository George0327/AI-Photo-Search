import { useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  Center,
  Container,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconRefresh, IconSparkles } from "@tabler/icons-react";

import type { DriveFileInfo } from "../api/driveApi";
import { fetchAlbums } from "../api/driveApi";
import {
  rebuildAlbumIndex,
  rebuildFullDriveIndex,
} from "../api/adminAiApi";
import type { DriveIndexResult } from "../api/adminAiApi";

export default function AdminPage() {
  const [albums, setAlbums] = useState<DriveFileInfo[]>([]);
  const [albumsLoading, setAlbumsLoading] = useState(false);
  const [indexingAlbumId, setIndexingAlbumId] = useState<string | null>(null);
  const [fullIndexing, setFullIndexing] = useState(false);
  const [result, setResult] = useState<DriveIndexResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadAlbums() {
    try {
      setError(null);
      setAlbumsLoading(true);
      const data = await fetchAlbums();
      setAlbums(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load albums.");
    } finally {
      setAlbumsLoading(false);
    }
  }

  async function handleIndexAlbum(album: DriveFileInfo) {
    try {
      setError(null);
      setResult(null);
      setIndexingAlbumId(album.id);

      const data = await rebuildAlbumIndex(album.id);
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to index album.");
    } finally {
      setIndexingAlbumId(null);
    }
  }

  async function handleRebuildFullIndex() {
    try {
      setError(null);
      setResult(null);
      setFullIndexing(true);

      const data = await rebuildFullDriveIndex();
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to rebuild full index.");
    } finally {
      setFullIndexing(false);
    }
  }

  useEffect(() => {
    loadAlbums();
  }, []);

  return (
    <Container size="xl" py="md">
      <Stack>
        <Group justify="space-between">
          <div>
            <Title order={2}>Admin</Title>
            <Text size="sm" c="dimmed">
              Manage AI indexing for Google Drive albums.
            </Text>
          </div>

          <Group>
            <Button
              variant="light"
              leftSection={<IconRefresh size={16} />}
              onClick={loadAlbums}
              loading={albumsLoading}
            >
              Refresh albums
            </Button>

            <Button
              leftSection={<IconSparkles size={16} />}
              onClick={handleRebuildFullIndex}
              loading={fullIndexing}
              color="red"
            >
              Rebuild full index
            </Button>
          </Group>
        </Group>

        <Alert color="yellow" title="Indexing behavior">
          Indexing an album currently resets the FAISS index first. That means the
          searchable photos become the photos from the last indexed album, unless
          you rebuild the full Drive index.
        </Alert>

        {error && (
          <Alert color="red" title="Error" icon={<IconAlertCircle size={18} />}>
            {error}
          </Alert>
        )}

        {albumsLoading ? (
          <Center py={80}>
            <Loader />
          </Center>
        ) : (
          <Card withBorder radius="md">
            <Group justify="space-between" mb="md">
              <Title order={4}>Albums</Title>
              <Badge variant="light">{albums.length}</Badge>
            </Group>

            <Table.ScrollContainer minWidth={700}>
              <Table verticalSpacing="sm">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Album</Table.Th>
                    <Table.Th>Drive folder ID</Table.Th>
                    <Table.Th>Action</Table.Th>
                  </Table.Tr>
                </Table.Thead>

                <Table.Tbody>
                  {albums.map((album) => (
                    <Table.Tr key={album.id}>
                      <Table.Td>
                        <Text fw={500}>{album.name}</Text>
                      </Table.Td>

                      <Table.Td>
                        <Text size="xs" c="dimmed">
                          {album.id}
                        </Text>
                      </Table.Td>

                      <Table.Td>
                        <Button
                          size="xs"
                          leftSection={<IconSparkles size={14} />}
                          onClick={() => handleIndexAlbum(album)}
                          loading={indexingAlbumId === album.id}
                          disabled={fullIndexing || indexingAlbumId !== null}
                        >
                          Index album
                        </Button>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>

            {albums.length === 0 && (
              <Text size="sm" c="dimmed" mt="md">
                No albums found.
              </Text>
            )}
          </Card>
        )}

        {result && (
          <Card withBorder radius="md">
            <Title order={4} mb="sm">
              Last indexing result
            </Title>

            <Group>
              <Badge>Albums: {result.albumCount}</Badge>
              <Badge>Total images: {result.totalImages}</Badge>
              <Badge color="green">Indexed: {result.indexedCount}</Badge>
              <Badge color={result.failedCount > 0 ? "red" : "gray"}>
                Failed: {result.failedCount}
              </Badge>
            </Group>

            {result.failures.length > 0 && (
              <Stack mt="md" gap="xs">
                <Text fw={500}>Failures</Text>

                {result.failures.map((failure) => (
                  <Alert key={failure.fileId} color="red">
                    <Text size="sm" fw={500}>
                      {failure.fileName}
                    </Text>
                    <Text size="xs">{failure.error}</Text>
                  </Alert>
                ))}
              </Stack>
            )}
          </Card>
        )}
      </Stack>
    </Container>
  );
}