import React, { useMemo } from "react";
import {
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  Title,
  Tooltip,
} from "@mantine/core";
import { IconFaceId, IconSparkles } from "@tabler/icons-react";
import type { AlbumIndexStatus } from "../../api/adminAiApi";

type AlbumIndexTableProps = {
  albums: AlbumIndexStatus[];
  indexingAlbumId: string | null;
  reindexingAlbumId: string | null;
  indexingFacesAlbumId: string | null;
  reindexingFacesAlbumId: string | null;
  anyBusy: boolean;
  onIndexAlbum: (album: AlbumIndexStatus) => void;
  onReindexAlbum: (album: AlbumIndexStatus) => void;
  onIndexFaces: (album: AlbumIndexStatus) => void;
  onReindexFaces: (album: AlbumIndexStatus) => void;
};

// Shared badge logic so the CLIP and Faces columns behave identically.
function indexBadge(
  busy: boolean,
  needsIndex: number,
  indexed: number,
  total: number,
  deleted: number,
): { color: string; label: string } {
  if (busy) return { color: "blue", label: "Indexing…" };
  if (deleted > 0) return { color: "red", label: "Reindex" };
  if (needsIndex > 0) return { color: "yellow", label: "New photos" };
  if (indexed > 0) return { color: "green", label: "Indexed" };
  if (total > 0) return { color: "gray", label: "Not indexed" };
  return { color: "gray", label: "No photos" };
}

export function AlbumIndexTable({
  albums,
  indexingAlbumId,
  reindexingAlbumId,
  indexingFacesAlbumId,
  reindexingFacesAlbumId,
  anyBusy,
  onIndexAlbum,
  onReindexAlbum,
  onIndexFaces,
  onReindexFaces,
}: AlbumIndexTableProps) {
  const { sortedKeys, grouped } = useMemo(() => {
    const m = new Map<string, AlbumIndexStatus[]>();
    for (const a of albums) {
      const key = a.year ?? "__none__";
      if (!m.has(key)) m.set(key, []);
      m.get(key)!.push(a);
    }
    const keys = [...m.keys()]
      .filter((k) => k !== "__none__")
      .sort((a, b) => b.localeCompare(a));
    if (m.has("__none__")) keys.push("__none__");
    return { sortedKeys: keys, grouped: m };
  }, [albums]);

  return (
    <Card withBorder radius="md">
      <Group justify="space-between" mb="md">
        <Title order={4}>Albums</Title>
        <Badge variant="light">{albums.length}</Badge>
      </Group>

      <Table.ScrollContainer minWidth={900}>
        <Table verticalSpacing="sm" striped highlightOnHover aria-label="Album indexing status">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Album</Table.Th>
              <Table.Th>Drive folder ID</Table.Th>
              <Table.Th>Total</Table.Th>
              <Table.Th>CLIP</Table.Th>
              <Table.Th>Faces</Table.Th>
              <Table.Th>Actions</Table.Th>
            </Table.Tr>
          </Table.Thead>

          <Table.Tbody>
            {sortedKeys.map((sectionKey) => (
              <React.Fragment key={sectionKey}>
                <Table.Tr>
                  <Table.Td
                    colSpan={6}
                    style={{ background: "var(--mantine-color-default-hover)", padding: "6px 12px" }}
                  >
                    <Text size="xs" fw={700} tt="uppercase" c="dimmed">
                      {sectionKey === "__none__" ? "Other" : sectionKey}
                    </Text>
                  </Table.Td>
                </Table.Tr>

                {grouped.get(sectionKey)!.map((album) => {
                  const isIndexingNow =
                    indexingAlbumId === album.driveAlbumId ||
                    reindexingAlbumId === album.driveAlbumId ||
                    album.indexingImages;
                  const isFacesIndexingNow =
                    indexingFacesAlbumId === album.driveAlbumId ||
                    reindexingFacesAlbumId === album.driveAlbumId ||
                    album.indexingFaces;

                  const clipBadge = indexBadge(
                    isIndexingNow, album.needsIndexImages, album.indexedImages,
                    album.totalActiveImages, album.deletedImages);
                  const faceBadge = indexBadge(
                    isFacesIndexingNow, album.faceNeedsIndexImages, album.faceIndexedImages,
                    album.totalActiveImages, 0);

                  return (
                    <Table.Tr key={album.driveAlbumId}>
                      <Table.Td>
                        <Text fw={500}>{album.title}</Text>
                      </Table.Td>

                      <Table.Td>
                        <Text size="xs" c="dimmed">{album.driveAlbumId}</Text>
                      </Table.Td>

                      <Table.Td>{album.totalActiveImages}</Table.Td>

                      <Table.Td>
                        <Tooltip
                          label={`${album.indexedImages} indexed · ${album.needsIndexImages} pending · ${album.deletedImages} deleted`}
                          withArrow
                        >
                          <Badge
                            color={clipBadge.color}
                            variant="light"
                            style={{ cursor: "default" }}
                            leftSection={isIndexingNow ? <Loader size={10} color="blue" /> : undefined}
                          >
                            {clipBadge.label}
                          </Badge>
                        </Tooltip>
                      </Table.Td>

                      <Table.Td>
                        <Tooltip
                          label={`${album.faceIndexedImages} indexed · ${album.faceNeedsIndexImages} pending`}
                          withArrow
                        >
                          <Badge
                            color={faceBadge.color}
                            variant="light"
                            leftSection={isFacesIndexingNow ? <Loader size={10} color="blue" /> : <IconFaceId size={10} />}
                            style={{ cursor: "default" }}
                          >
                            {faceBadge.label}
                          </Badge>
                        </Tooltip>
                      </Table.Td>

                      <Table.Td>
                        <Group gap="xs" wrap="wrap">
                          <Button
                            size="xs"
                            leftSection={<IconSparkles size={14} />}
                            onClick={() => onIndexAlbum(album)}
                            loading={isIndexingNow}
                            disabled={anyBusy || album.needsIndexImages === 0}
                          >
                            Index new
                          </Button>

                          <Button
                            size="xs"
                            variant="light"
                            color={album.deletedImages > 0 ? "red" : "gray"}
                            onClick={() => onReindexAlbum(album)}
                            loading={reindexingAlbumId === album.driveAlbumId}
                            disabled={anyBusy || album.indexedImages === 0}
                          >
                            Reindex
                          </Button>

                          <Button
                            size="xs"
                            variant="light"
                            color="teal"
                            leftSection={<IconFaceId size={14} />}
                            onClick={() => onIndexFaces(album)}
                            loading={indexingFacesAlbumId === album.driveAlbumId}
                            disabled={anyBusy || album.faceNeedsIndexImages === 0}
                          >
                            Index new faces
                          </Button>

                          <Button
                            size="xs"
                            variant="light"
                            color="gray"
                            onClick={() => onReindexFaces(album)}
                            loading={reindexingFacesAlbumId === album.driveAlbumId}
                            disabled={anyBusy || album.faceIndexedImages === 0}
                          >
                            Reindex faces
                          </Button>

                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </React.Fragment>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>

      {albums.length === 0 && (
        <Stack align="center" gap="xs" mt="md">
          <Text size="sm" c="dimmed">No albums found. Click "Sync &amp; Refresh" to load from Google Drive.</Text>
        </Stack>
      )}
    </Card>
  );
}
