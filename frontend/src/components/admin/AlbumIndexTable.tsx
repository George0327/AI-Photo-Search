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
import { IconFaceId, IconSearch, IconSparkles } from "@tabler/icons-react";
import type { AlbumIndexStatus } from "../../api/adminAiApi";

type AlbumIndexTableProps = {
  albums: AlbumIndexStatus[];
  indexingAlbumId: string | null;
  reindexingAlbumId: string | null;
  indexingFacesAlbumId: string | null;
  indexingLogosAlbumId: string | null;
  anyBusy: boolean;
  onIndexAlbum: (album: AlbumIndexStatus) => void;
  onReindexAlbum: (album: AlbumIndexStatus) => void;
  onIndexFaces: (album: AlbumIndexStatus) => void;
  onIndexLogos: (album: AlbumIndexStatus) => void;
};

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

const YEAR_RE = /^\d{4}$/;

export function AlbumIndexTable({
  albums,
  indexingAlbumId,
  reindexingAlbumId,
  indexingFacesAlbumId,
  indexingLogosAlbumId,
  anyBusy,
  onIndexAlbum,
  onReindexAlbum,
  onIndexFaces,
  onIndexLogos,
}: AlbumIndexTableProps) {
  const { sortedKeys, grouped } = useMemo(() => {
    const m = new Map<string, AlbumIndexStatus[]>();
    for (const a of albums) {
      const key = YEAR_RE.test(a.title) ? a.title : "__none__";
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
              <Table.Th>Logos</Table.Th>
              <Table.Th>Actions</Table.Th>
            </Table.Tr>
          </Table.Thead>

          <Table.Tbody>
            {sortedKeys.map((sectionKey) => (
              <React.Fragment key={sectionKey}>
                <Table.Tr>
                  <Table.Td
                    colSpan={7}
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
                    reindexingAlbumId === album.driveAlbumId;

                  const clipBadgeColor = isIndexingNow
                    ? "blue"
                    : album.deletedImages > 0
                    ? "red"
                    : album.needsIndexImages > 0
                    ? "yellow"
                    : album.totalActiveImages > 0
                    ? "green"
                    : "gray";

                  const clipBadgeLabel = isIndexingNow
                    ? "Indexing…"
                    : album.deletedImages > 0
                    ? "Reindex"
                    : album.needsIndexImages > 0
                    ? "New photos"
                    : album.totalActiveImages > 0
                    ? "Indexed"
                    : "No photos";

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
                            color={clipBadgeColor}
                            variant="light"
                            style={{ cursor: "default" }}
                            leftSection={isIndexingNow ? <Loader size={10} color="blue" /> : undefined}
                          >
                            {clipBadgeLabel}
                          </Badge>
                        </Tooltip>
                      </Table.Td>

                      <Table.Td>
                        <Tooltip
                          label={album.faceIndexedAt ? `Indexed: ${formatDate(album.faceIndexedAt)}` : "Not indexed"}
                          withArrow
                        >
                          <Badge
                            size="sm"
                            color={album.faceIndexedAt ? "teal" : "gray"}
                            variant="light"
                            leftSection={<IconFaceId size={10} />}
                            style={{ cursor: "default" }}
                          >
                            {album.faceIndexedAt ? "Indexed" : "—"}
                          </Badge>
                        </Tooltip>
                      </Table.Td>

                      <Table.Td>
                        <Tooltip
                          label={album.logoIndexedAt ? `Indexed: ${formatDate(album.logoIndexedAt)}` : "Not indexed"}
                          withArrow
                        >
                          <Badge
                            size="sm"
                            color={album.logoIndexedAt ? "teal" : "gray"}
                            variant="light"
                            leftSection={<IconSearch size={10} />}
                            style={{ cursor: "default" }}
                          >
                            {album.logoIndexedAt ? "Indexed" : "—"}
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
                            disabled={anyBusy || album.totalActiveImages === 0}
                          >
                            Faces
                          </Button>

                          <Button
                            size="xs"
                            variant="light"
                            color="violet"
                            leftSection={<IconSearch size={14} />}
                            onClick={() => onIndexLogos(album)}
                            loading={indexingLogosAlbumId === album.driveAlbumId}
                            disabled={anyBusy || album.totalActiveImages === 0}
                          >
                            Logos
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
