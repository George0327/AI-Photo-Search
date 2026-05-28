import {
  Badge,
  Button,
  Card,
  Group,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { IconSparkles } from "@tabler/icons-react";
import type { AlbumIndexStatus } from "../../api/adminAiApi";

type AlbumIndexTableProps = {
  albums: AlbumIndexStatus[];
  indexingAlbumId: string | null;
  reindexingAlbumId: string | null;
  anyBusy: boolean;
  onIndexAlbum: (album: AlbumIndexStatus) => void;
  onReindexAlbum: (album: AlbumIndexStatus) => void;
};

export function AlbumIndexTable({
  albums,
  indexingAlbumId,
  reindexingAlbumId,
  anyBusy,
  onIndexAlbum,
  onReindexAlbum,
}: AlbumIndexTableProps) {
  return (
    <Card withBorder radius="md">
      <Group justify="space-between" mb="md">
        <Title order={4}>Albums</Title>
        <Badge variant="light">{albums.length}</Badge>
      </Group>

      <Table.ScrollContainer minWidth={1000}>
        <Table verticalSpacing="sm" striped highlightOnHover aria-label="Album indexing status">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Album</Table.Th>
              <Table.Th>Drive folder ID</Table.Th>
              <Table.Th>Total active</Table.Th>
              <Table.Th>Indexed</Table.Th>
              <Table.Th>Needs indexing</Table.Th>
              <Table.Th>Deleted</Table.Th>
              <Table.Th>Status</Table.Th>
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

              return (
                <Table.Tr key={album.driveAlbumId}>
                  <Table.Td>
                    <Text fw={500}>{album.title}</Text>
                  </Table.Td>

                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {album.driveAlbumId}
                    </Text>
                  </Table.Td>

                  <Table.Td>{album.totalActiveImages}</Table.Td>

                  <Table.Td>
                    <Badge color="green" variant="light">
                      {album.indexedImages}
                    </Badge>
                  </Table.Td>

                  <Table.Td>
                    <Badge
                      color={hasNewPhotos ? "yellow" : "gray"}
                      variant="light"
                    >
                      {album.needsIndexImages}
                    </Badge>
                  </Table.Td>

                  <Table.Td>
                    <Badge
                      color={hasDeletedPhotos ? "red" : "gray"}
                      variant="light"
                    >
                      {album.deletedImages}
                    </Badge>
                  </Table.Td>

                  <Table.Td>
                    {hasDeletedPhotos ? (
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
                    <Group gap="xs">
                      <Button
                        size="xs"
                        leftSection={<IconSparkles size={14} />}
                        onClick={() => onIndexAlbum(album)}
                        loading={indexingAlbumId === album.driveAlbumId}
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
                        Reindex album
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
        <Text size="sm" c="dimmed" mt="md">
          No albums found.
        </Text>
      )}
    </Card>
  );
}
