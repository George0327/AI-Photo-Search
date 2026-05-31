import {
  Badge,
  Card,
  Center,
  Divider,
  Group,
  Image,
  Loader,
  Pagination,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
} from "@mantine/core";
import { IconPhoto, IconSearch } from "@tabler/icons-react";
import type { DriveFileInfo } from "../api/driveApi";
import { getImageThumbnailUrl } from "../api/driveApi";

type PhotoGridProps = {
  images: DriveFileInfo[];
  loading: boolean;
  searchMode: boolean;
  searchLabel?: string;
  activePage: number;
  totalPages: number;
  totalCount: number;
  firstVisible: number;
  lastVisible: number;
  scores?: Record<string, number>;
  onPageChange: (page: number) => void;
  onImageClick: (image: DriveFileInfo) => void;
};

function scoreColor(score: number): string {
  if (score >= 0.8) return "green";
  if (score >= 0.6) return "brand";
  return "gray";
}

export function PhotoGrid({
  images,
  loading,
  searchMode,
  searchLabel = "AI search results",
  activePage,
  totalPages,
  totalCount,
  firstVisible,
  lastVisible,
  scores,
  onPageChange,
  onImageClick,
}: PhotoGridProps) {
  return (
    <Paper
      withBorder
      radius="md"
      p="md"
      mih={300}
      style={{ background: searchMode ? "var(--mantine-color-violet-light)" : undefined }}
    >
      {searchMode && totalCount > 0 && (
        <Group mb="md" gap="xs">
          <ThemeIcon size="sm" variant="light" color="brand">
            <IconSearch size={12} />
          </ThemeIcon>
          <Text size="sm" fw={500} c="brand">
            {searchLabel}
          </Text>
        </Group>
      )}

      {loading ? (
        <Center h={300}>
          <Loader type="bars" color="brand" />
        </Center>
      ) : (
        <>
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3, lg: 4, xl: 5 }} spacing="sm">
            {images.map((image) => {
              const score = scores?.[image.id];
              return (
                <Card
                  key={image.id}
                  className="photo-card"
                  padding="xs"
                  radius="sm"
                  withBorder
                  role="button"
                  tabIndex={0}
                  onClick={() => onImageClick(image)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") onImageClick(image);
                  }}
                >
                  <Card.Section style={{ position: "relative" }}>
                    <Image
                      src={getImageThumbnailUrl(image.id)}
                      alt={image.name}
                      h={200}
                      fit="cover"
                      loading="lazy"
                    />
                    {score !== undefined && (
                      <Badge
                        color={scoreColor(score)}
                        variant="filled"
                        size="sm"
                        style={{ position: "absolute", top: 8, right: 8 }}
                      >
                        {Math.max(0, Math.round(score * 100))}%
                      </Badge>
                    )}
                  </Card.Section>
                  <Text size="sm" mt="sm" fw={500} truncate="end">
                    {image.name}
                  </Text>
                </Card>
              );
            })}
          </SimpleGrid>

          {totalPages > 1 && (
            <>
              <Divider mt="xl" />
              <Group justify="space-between" mt="md">
                <Text size="sm" c="dimmed">
                  Showing {firstVisible}–{lastVisible} of {totalCount}
                </Text>
                <Pagination
                  total={totalPages}
                  value={activePage}
                  onChange={onPageChange}
                  color="brand"
                />
              </Group>
            </>
          )}

          {totalCount === 0 && (
            <Center h={300}>
              <Stack align="center" gap="xs">
                <ThemeIcon size={48} radius="xl" variant="light" color="gray">
                  <IconPhoto size={24} />
                </ThemeIcon>
                <Text c="dimmed" ta="center">
                  {searchMode
                    ? "No matches found in the selected AI index."
                    : "This album is empty."}
                </Text>
              </Stack>
            </Center>
          )}
        </>
      )}
    </Paper>
  );
}
