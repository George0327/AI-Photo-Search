import {
  Alert,
  Button,
  Center,
  Container,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconRefresh, IconSparkles } from "@tabler/icons-react";
import { useAdminIndexing } from "../hooks/useAdminIndexing";
import { AlbumIndexTable } from "../components/admin/AlbumIndexTable";

export default function AdminPage() {
  const {
    albums,
    albumsLoading,
    indexingAlbumId,
    reindexingAlbumId,
    indexingAll,
    indexingFacesAlbumId,
    anyAlbumBusy,
    failures,
    error,
    syncAlbumStatuses,
    handleIndexAlbum,
    handleReindexAlbum,
    handleIndexAllNewPhotos,
    handleIndexFaces,
  } = useAdminIndexing();

  const totalActive = albums.reduce((s, a) => s + a.totalActiveImages, 0);
  const totalIndexed = albums.reduce((s, a) => s + a.indexedImages, 0);
  const totalPending = albums.reduce((s, a) => s + a.needsIndexImages, 0);
  const totalDeleted = albums.reduce((s, a) => s + a.deletedImages, 0);

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
              onClick={syncAlbumStatuses}
              loading={albumsLoading}
              disabled={anyAlbumBusy}
            >
              Refresh statuses
            </Button>

            <Button
              leftSection={<IconSparkles size={16} />}
              onClick={handleIndexAllNewPhotos}
              loading={indexingAll}
              disabled={indexingAlbumId !== null || reindexingAlbumId !== null}
            >
              Index all new photos
            </Button>
          </Group>
        </Group>

        {albums.length > 0 && (
          <SimpleGrid cols={{ base: 2, sm: 4 }}>
            {[
              { label: "Albums", value: albums.length, color: "brand" },
              { label: "Total photos", value: totalActive, color: "gray" },
              { label: "Indexed", value: totalIndexed, color: "green" },
              {
                label: "Pending",
                value: totalPending,
                color: totalPending > 0 ? "yellow" : "gray",
              },
              ...(totalDeleted > 0
                ? [{ label: "Deleted", value: totalDeleted, color: "red" }]
                : []),
            ].map(({ label, value, color }) => (
              <Paper key={label} withBorder radius="md" p="md">
                <Text size="xs" c="dimmed" tt="uppercase" fw={600} mb={4}>
                  {label}
                </Text>
                <Text size="xl" fw={700} c={color}>
                  {value}
                </Text>
              </Paper>
            ))}
          </SimpleGrid>
        )}

        <Alert color="blue" title="Indexing behavior">
          New or failed photos can be indexed incrementally. If an album has
          deleted photos, reindex the whole album to clean that album&apos;s AI
          index folder.
        </Alert>

        {error && (
          <Alert color="red" title="Error" icon={<IconAlertCircle size={18} />}>
            {error}
          </Alert>
        )}

        {failures.length > 0 && (
          <Stack gap="xs">
            <Text fw={500}>Indexing failures</Text>
            {failures.map((failure) => (
              <Alert key={failure.fileId} color="red">
                <Text size="sm" fw={500}>
                  {failure.fileName}
                </Text>
                <Text size="xs">{failure.error}</Text>
              </Alert>
            ))}
          </Stack>
        )}

        {albumsLoading ? (
          <Center py={80}>
            <Loader />
          </Center>
        ) : (
          <AlbumIndexTable
            albums={albums}
            indexingAlbumId={indexingAlbumId}
            reindexingAlbumId={reindexingAlbumId}
            indexingFacesAlbumId={indexingFacesAlbumId}
            anyBusy={anyAlbumBusy}
            onIndexAlbum={handleIndexAlbum}
            onReindexAlbum={handleReindexAlbum}
            onIndexFaces={handleIndexFaces}
          />
        )}
      </Stack>
    </Container>
  );
}
