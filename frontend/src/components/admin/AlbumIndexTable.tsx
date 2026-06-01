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
  return (
    <Card withBorder radius="md">
      <Group justify="space-between" mb="md">
        <Title order={4}>Albums</Title>
        <Badge variant="light">{albums.length}</Badge>
      </Group>

      <Table.ScrollContainer minWidth={1300}>
        <Table verticalSpacing="sm" striped highlightOnHover aria-label="Album indexing status">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Album</Table.Th>
              <Table.Th>Drive folder ID</Table.Th>
              <Table.Th>Total active</Table.Th>
              <Table.Th>Indexed</Table.Th>
              <Table.Th>Needs indexing</Table.Th>
              <Table.Th>Deleted</Table.Th>
              <Table.Th>CLIP status</Table.Th>
              <Table.Th>Faces / Logos</Table.Th>
              <Table.Th>Actions</Table.Th>
            </Table.Tr>
          </Table.Thead>

          <Table.Tbody>
            {albums.map((album) => {
              const hasNewPhotos = album.needsIndexImages > 0;
              const hasDeletedPhotos = album.deletedImages > 0;
              const fullyIndexed =
                album.totalActiveImages > 0 &&
                album.needsIndexImages === 0 &&
                album.deletedImages === 0;
              const isIndexingNow =
                indexingAlbumId === album.driveAlbumId ||
                reindexingAlbumId === album.driveAlbumId;

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
                    <Badge color="green" variant="light">{album.indexedImages}</Badge>
                  </Table.Td>

                  <Table.Td>
                    <Badge color={hasNewPhotos ? "yellow" : "gray"} variant="light">
                      {album.needsIndexImages}
                    </Badge>
                  </Table.Td>

                  <Table.Td>
                    <Badge color={hasDeletedPhotos ? "red" : "gray"} variant="light">
                      {album.deletedImages}
                    </Badge>
                  </Table.Td>

                  <Table.Td>
                    {isIndexingNow ? (
                      <Badge color="blue" leftSection={<Loader size={10} color="blue" />}>
                        Indexing
                      </Badge>
                    ) : hasDeletedPhotos ? (
                      <Badge color="red">Reindex recommended</Badge>
                    ) : hasNewPhotos ? (
                      <Badge color="yellow">New photos</Badge>
                    ) : fullyIndexed ? (
                      <Badge color="green">Indexed</Badge>
                    ) : (
                      <Badge color="gray">No photos</Badge>
                    )}
                  </Table.Td>

                  <Table.Td>
                    <Stack gap={4}>
                      <Tooltip label={album.faceIndexedAt ? `Indexed ${formatDate(album.faceIndexedAt)}` : "Not indexed"}>
                        <Badge
                          size="sm"
                          color={album.faceIndexedAt ? "teal" : "gray"}
                          variant="light"
                          leftSection={<IconFaceId size={10} />}
                          style={{ cursor: "default" }}
                        >
                          Faces
                        </Badge>
                      </Tooltip>
                      <Tooltip label={album.logoIndexedAt ? `Indexed ${formatDate(album.logoIndexedAt)}` : "Not indexed"}>
                        <Badge
                          size="sm"
                          color={album.logoIndexedAt ? "teal" : "gray"}
                          variant="light"
                          leftSection={<IconSearch size={10} />}
                          style={{ cursor: "default" }}
                        >
                          Logos
                        </Badge>
                      </Tooltip>
                    </Stack>
                  </Table.Td>

                  <Table.Td>
                    <Group gap="xs" wrap="wrap">
                      <Button
                        size="xs"
                        leftSection={<IconSparkles size={14} />}
                        onClick={() => onIndexAlbum(album)}
                        loading={isIndexingNow}
                        disabled={anyBusy || !hasNewPhotos}
                      >
                        Index new
                      </Button>

                      <Button
                        size="xs"
                        variant="light"
                        color={hasDeletedPhotos ? "red" : "gray"}
                        onClick={() => onReindexAlbum(album)}
                        loading={reindexingAlbumId === album.driveAlbumId}
                        disabled={anyBusy || !hasDeletedPhotos}
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
                        Index faces
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
                        Index logos
                      </Button>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>

      {albums.length === 0 && (
        <Text size="sm" c="dimmed" mt="md">No albums found.</Text>
      )}
    </Card>
  );
}
