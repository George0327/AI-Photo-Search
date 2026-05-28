import {
  Badge,
  Button,
  Center,
  Checkbox,
  Group,
  Loader,
  Paper,
  ScrollArea,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import { IconPhoto } from "@tabler/icons-react";
import type { DriveFileInfo } from "../api/driveApi";

const SIDEBAR_SCROLL_HEIGHT = "calc(100vh - 280px)";

type AlbumSidebarProps = {
  albums: DriveFileInfo[];
  albumsLoading: boolean;
  selectedAlbum: DriveFileInfo | null;
  selectedSearchAlbumIds: string[];
  onSelectAlbum: (album: DriveFileInfo) => void;
  onSearchAlbumToggle: (albumId: string, checked: boolean) => void;
  onSelectAllForSearch: () => void;
  onClearSearchSelection: () => void;
};

export function AlbumSidebar({
  albums,
  albumsLoading,
  selectedAlbum,
  selectedSearchAlbumIds,
  onSelectAlbum,
  onSearchAlbumToggle,
  onSelectAllForSearch,
  onClearSearchSelection,
}: AlbumSidebarProps) {
  return (
    <Paper withBorder radius="md" p="md" h="100%">
      <Stack gap="md">
        <Group justify="space-between">
          <div>
            <Title order={5}>Albums</Title>
            <Text size="xs" c="dimmed">
              Scope your search
            </Text>
          </div>
          <Badge variant="light" color="brand">
            {albums.length}
          </Badge>
        </Group>

        <Paper radius="sm" p="sm" style={{ background: "var(--mantine-color-brand-light)" }}>
          <Group justify="space-between">
            <Text size="xs" fw={600} c="brand.8">
              Search scope
            </Text>
            <Group gap={4}>
              <Button variant="subtle" size="compact-xs" onClick={onSelectAllForSearch}>
                All
              </Button>
              <Button variant="subtle" size="compact-xs" onClick={onClearSearchSelection}>
                None
              </Button>
            </Group>
          </Group>
          <Text size="xs" c="dimmed" mt={4}>
            {selectedSearchAlbumIds.length} of {albums.length} selected
          </Text>
        </Paper>

        {albumsLoading ? (
          <Center py="xl">
            <Loader size="sm" />
          </Center>
        ) : (
          <ScrollArea h={SIDEBAR_SCROLL_HEIGHT} offsetScrollbars>
            <Stack gap={4}>
              {albums.map((album) => {
                const checkedForSearch = selectedSearchAlbumIds.includes(album.id);
                const isBrowsing = selectedAlbum?.id === album.id;
                return (
                  <Group
                    key={album.id}
                    gap="xs"
                    wrap="nowrap"
                    p="xs"
                    style={{
                      borderRadius: "var(--mantine-radius-sm)",
                      borderLeft: `3px solid ${isBrowsing ? "var(--mantine-color-brand-filled)" : "transparent"}`,
                      background: isBrowsing
                        ? "var(--mantine-color-brand-light)"
                        : "transparent",
                      cursor: "default",
                    }}
                  >
                    <Group
                      gap="xs"
                      wrap="nowrap"
                      style={{ flex: 1, minWidth: 0, cursor: "pointer" }}
                      onClick={() => onSelectAlbum(album)}
                    >
                      <ThemeIcon
                        size="sm"
                        variant="transparent"
                        color={isBrowsing ? "brand" : "gray"}
                      >
                        <IconPhoto size={14} />
                      </ThemeIcon>
                      <Text
                        size="sm"
                        truncate="end"
                        fw={isBrowsing ? 600 : 400}
                        c={isBrowsing ? "brand" : undefined}
                      >
                        {album.name}
                      </Text>
                    </Group>
                    <Checkbox
                      checked={checkedForSearch}
                      aria-label={album.name}
                      size="xs"
                      onChange={(e) =>
                        onSearchAlbumToggle(album.id, e.currentTarget.checked)
                      }
                    />
                  </Group>
                );
              })}
            </Stack>
          </ScrollArea>
        )}
      </Stack>
    </Paper>
  );
}
