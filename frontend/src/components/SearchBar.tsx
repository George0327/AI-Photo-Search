import { useRef } from "react";
import {
  Button,
  Group,
  Kbd,
  Loader,
  SegmentedControl,
  Select,
  Stack,
  TextInput,
} from "@mantine/core";
import { useHotkeys } from "@mantine/hooks";
import { IconSearch } from "@tabler/icons-react";
import { ReferenceImageUpload } from "./ReferenceImageUpload";

export type SearchMode = "text" | "face" | "logo";

export const TOP_K_OPTIONS = ["10", "20", "50", "100"];

type SearchBarProps = {
  mode: SearchMode;
  value: string;
  topK: string;
  loading: boolean;
  searchActive: boolean;
  referenceFiles: File[];
  onChange: (value: string) => void;
  onModeChange: (mode: SearchMode) => void;
  onTopKChange: (value: string) => void;
  onReferenceFilesChange: (files: File[]) => void;
  onSearch: () => void;
  onClear: () => void;
};

export function SearchBar({
  mode,
  value,
  topK,
  loading,
  searchActive,
  referenceFiles,
  onChange,
  onModeChange,
  onTopKChange,
  onReferenceFilesChange,
  onSearch,
  onClear,
}: SearchBarProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  useHotkeys([["slash", () => mode === "text" && inputRef.current?.focus()]]);

  return (
    <Stack gap="xs">
      <Group gap="sm">
        <SegmentedControl
          value={mode}
          onChange={(v) => onModeChange(v as SearchMode)}
          size="sm"
          data={[
            { label: "Text", value: "text" },
            { label: "Face", value: "face" },
            { label: "Logo", value: "logo" },
          ]}
        />
      </Group>

      <Group align="flex-end" gap="sm">
        {mode === "text" ? (
          <TextInput
            ref={inputRef}
            size="md"
            radius="md"
            placeholder='Describe a photo (e.g., "dog playing in the park")'
            value={value}
            onChange={(e) => onChange(e.currentTarget.value)}
            onKeyDown={(e) => e.key === "Enter" && onSearch()}
            leftSection={
              <IconSearch size={18} color="var(--mantine-color-gray-5)" />
            }
            rightSection={
              loading ? (
                <Loader size="xs" color="brand" />
              ) : (
                <Kbd size="xs">↵ Enter</Kbd>
              )
            }
            rightSectionWidth={70}
            style={{ flex: 1 }}
          />
        ) : (
          <ReferenceImageUpload
            files={referenceFiles}
            onFilesChange={onReferenceFilesChange}
            label={mode === "face" ? "photos of the person" : "photos of the logo"}
          />
        )}

        <Select
          size="md"
          radius="md"
          value={topK}
          onChange={(val) => onTopKChange(val ?? "10")}
          data={TOP_K_OPTIONS}
          w={90}
        />

        {mode !== "text" && (
          <Button
            size="md"
            loading={loading}
            disabled={referenceFiles.length === 0}
            onClick={onSearch}
          >
            Search
          </Button>
        )}

        {searchActive && (
          <Button size="md" variant="light" color="gray" onClick={onClear}>
            Clear
          </Button>
        )}
      </Group>
    </Stack>
  );
}
