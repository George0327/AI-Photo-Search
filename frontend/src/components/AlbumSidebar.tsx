import { memo, useMemo } from "react";
import {
  Accordion,
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
  Tooltip,
} from "@mantine/core";
import { IconPhoto } from "@tabler/icons-react";
import type { AlbumSearchStatus, DriveFileInfo } from "../api/driveApi";
import { SIDEBAR_SCROLL_HEIGHT } from "../constants";

type AlbumSidebarProps = {
  albums: DriveFileInfo[];
  albumStatuses: Record<string, AlbumSearchStatus>;
  albumsLoading: boolean;
  selectedAlbum: DriveFileInfo | null;
  selectedSearchAlbumIds: string[];
  onSelectAlbum: (album: DriveFileInfo) => void;
  onSearchAlbumToggle: (albumId: string, checked: boolean) => void;
  onSelectAllForSearch: () => void;
  onClearSearchSelection: () => void;
};

type AlbumRowProps = {
  album: DriveFileInfo;
  status?: AlbumSearchStatus;
  isBrowsing: boolean;
  checkedForSearch: boolean;
  onSelectAlbum: (album: DriveFileInfo) => void;
  onSearchAlbumToggle: (albumId: string, checked: boolean) => void;
};

function indexDotColor(indexed: number, pending: number): string {
  if (indexed > 0 && pending === 0) return "green";
  if (indexed > 0) return "yellow";
  return "gray";
}

function indexTooltip(kind: string, indexed: number, total: number): string {
  if (indexed === 0) return `${kind}: not indexed yet`;
  if (indexed >= total) return `${kind}: all ${total} photos searchable`;
  return `${kind}: ${indexed} of ${total} photos searchable`;
}

// Memoized: with hundreds of albums in the sidebar, only rows whose selection
// state changed should re-render.
const AlbumRow = memo(function AlbumRow({
  album, status, isBrowsing, checkedForSearch, onSelectAlbum, onSearchAlbumToggle,
}: AlbumRowProps) {
  return (
    <Group
      gap="xs"
      wrap="nowrap"
      p="xs"
      style={{
        borderRadius: "var(--mantine-radius-sm)",
        borderLeft: `3px solid ${isBrowsing ? "var(--mantine-color-brand-filled)" : "transparent"}`,
        background: isBrowsing ? "var(--mantine-color-brand-light)" : "transparent",
        cursor: "default",
      }}
    >
      <Group
        gap="xs"
        wrap="nowrap"
        style={{ flex: 1, minWidth: 0, cursor: "pointer" }}
        onClick={() => onSelectAlbum(album)}
      >
        <ThemeIcon size="sm" variant="transparent" color={isBrowsing ? "brand" : "gray"}>
          <IconPhoto size={14} />
        </ThemeIcon>
        <Text size="sm" truncate="end" fw={isBrowsing ? 600 : 400} c={isBrowsing ? "brand" : undefined}>
          {album.name}
        </Text>
      </Group>

      {status && (
        <Group gap={4} wrap="nowrap">
          <Tooltip label={indexTooltip("Text search", status.clipIndexedImages, status.totalActiveImages)} withArrow>
            <Badge size="xs" variant="dot" color={indexDotColor(status.clipIndexedImages, status.clipPendingImages)}
                   style={{ cursor: "default" }}>
              AI
            </Badge>
          </Tooltip>
          <Tooltip label={indexTooltip("Face search", status.faceIndexedImages, status.totalActiveImages)} withArrow>
            <Badge size="xs" variant="dot" color={indexDotColor(status.faceIndexedImages, status.facePendingImages)}
                   style={{ cursor: "default" }}>
              Face
            </Badge>
          </Tooltip>
        </Group>
      )}

      <Checkbox
        checked={checkedForSearch}
        aria-label={album.name}
        size="xs"
        onChange={(e) => onSearchAlbumToggle(album.id, e.currentTarget.checked)}
      />
    </Group>
  );
});

const ACCORDION_STYLES = {
  item: { borderBottom: "none" },
  control: { paddingLeft: 4, paddingRight: 4, paddingTop: 4, paddingBottom: 4 },
  panel: { paddingLeft: 0, paddingRight: 0 },
  content: { padding: 0 },
} as const;

export function AlbumSidebar({
  albums,
  albumStatuses,
  albumsLoading,
  selectedAlbum,
  selectedSearchAlbumIds,
  onSelectAlbum,
  onSearchAlbumToggle,
  onSelectAllForSearch,
  onClearSearchSelection,
}: AlbumSidebarProps) {
  // Albums checked for search are mirrored into a "Selected" accordion at the
  // top (collapsed by default) for a quick overview — they also stay in their
  // year groups below.
  const { selectedAlbums, yearGroups, sortedYears, ungrouped } = useMemo(() => {
    const selectedIdSet = new Set(selectedSearchAlbumIds);
    const selectedList: DriveFileInfo[] = [];
    const groups = new Map<string, DriveFileInfo[]>();
    const ungroupedList: DriveFileInfo[] = [];
    for (const album of albums) {
      if (selectedIdSet.has(album.id)) selectedList.push(album);
      if (album.year) {
        if (!groups.has(album.year)) groups.set(album.year, []);
        groups.get(album.year)!.push(album);
      } else {
        ungroupedList.push(album);
      }
    }
    const years = [...groups.keys()].sort((a, b) => b.localeCompare(a));
    return { selectedAlbums: selectedList, yearGroups: groups, sortedYears: years, ungrouped: ungroupedList };
  }, [albums, selectedSearchAlbumIds]);

  const renderRow = (album: DriveFileInfo) => (
    <AlbumRow
      key={album.id}
      album={album}
      status={albumStatuses[album.id]}
      isBrowsing={selectedAlbum?.id === album.id}
      checkedForSearch={selectedSearchAlbumIds.includes(album.id)}
      onSelectAlbum={onSelectAlbum}
      onSearchAlbumToggle={onSearchAlbumToggle}
    />
  );

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

        <Paper radius="sm" p="sm" withBorder style={{ background: "var(--mantine-color-brand-light)" }}>
          <Group justify="space-between">
            <Text size="sm" fw={700}>
              Search scope
            </Text>
            <Group gap={4}>
              <Button variant="light" size="compact-xs" onClick={onSelectAllForSearch}>
                All
              </Button>
              <Button variant="light" size="compact-xs" onClick={onClearSearchSelection}>
                None
              </Button>
            </Group>
          </Group>
          <Text size="sm" mt={4}>
            {selectedSearchAlbumIds.length} of {albums.length} selected
          </Text>
        </Paper>

        {albumsLoading ? (
          <Center py="xl">
            <Loader size="sm" />
          </Center>
        ) : (
          <ScrollArea h={SIDEBAR_SCROLL_HEIGHT} offsetScrollbars>
            {selectedAlbums.length > 0 && (
              <Accordion chevronPosition="left" styles={ACCORDION_STYLES} mb="xs">
                <Accordion.Item value="selected">
                  <Accordion.Control>
                    <Group gap="xs">
                      <Text size="sm" fw={600}>Selected</Text>
                      <Badge size="xs" variant="filled" color="brand">{selectedAlbums.length}</Badge>
                    </Group>
                  </Accordion.Control>
                  <Accordion.Panel>
                    <Stack gap={4}>{selectedAlbums.map(renderRow)}</Stack>
                  </Accordion.Panel>
                </Accordion.Item>
              </Accordion>
            )}

            {ungrouped.length > 0 && (
              <Stack gap={4} mb={sortedYears.length > 0 ? "xs" : 0}>
                {ungrouped.map(renderRow)}
              </Stack>
            )}

            {sortedYears.length > 0 && (
              <Accordion
                multiple
                defaultValue={sortedYears}
                chevronPosition="left"
                styles={ACCORDION_STYLES}
              >
                {sortedYears.map((year) => (
                  <Accordion.Item key={year} value={year}>
                    <Accordion.Control>
                      <Text size="sm" fw={600}>{year}</Text>
                    </Accordion.Control>
                    <Accordion.Panel>
                      <Stack gap={4}>
                        {yearGroups.get(year)!.map(renderRow)}
                      </Stack>
                    </Accordion.Panel>
                  </Accordion.Item>
                ))}
              </Accordion>
            )}

            {albums.length === 0 && (
              <Center py="xl">
                <Stack align="center" gap="xs">
                  <ThemeIcon size={40} radius="xl" variant="light" color="gray">
                    <IconPhoto size={20} />
                  </ThemeIcon>
                  <Text size="sm" c="dimmed" ta="center">No albums found.</Text>
                </Stack>
              </Center>
            )}
          </ScrollArea>
        )}
      </Stack>
    </Paper>
  );
}
