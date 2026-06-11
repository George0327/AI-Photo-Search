import { useRef } from "react";
import {
  Button,
  Group,
  Loader,
  NumberInput,
  SegmentedControl,
  Stack,
  TextInput,
} from "@mantine/core";
import { useHotkeys } from "@mantine/hooks";
import { IconSearch } from "@tabler/icons-react";
import { ReferenceImageUpload } from "./ReferenceImageUpload";

export type SearchMode = "text" | "face";


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
          ]}
        />
      </Group>

      <Group align="flex-end" gap="sm">
        {mode === "text" ? (
          <TextInput
            ref={inputRef}
            size="md"
            radius="md"
            placeholder='Describe a photo (e.g., "people at a picnic")'
            value={value}
            onChange={(e) => onChange(e.currentTarget.value)}
            onKeyDown={(e) => e.key === "Enter" && onSearch()}
            leftSection={
              <IconSearch size={18} color="var(--mantine-color-gray-5)" />
            }
            rightSection={loading ? <Loader size="xs" color="brand" /> : undefined}
            rightSectionWidth={loading ? 40 : undefined}
            style={{ flex: 1 }}
          />
        ) : (
          <ReferenceImageUpload
            files={referenceFiles}
            onFilesChange={onReferenceFilesChange}
            label="photos of the person"
          />
        )}

        <NumberInput
          size="md"
          radius="md"
          value={Number(topK)}
          onChange={(val) => onTopKChange(String(val || 10))}
          min={1}
          step={10}
          w={90}
        />

        <Button
          size="md"
          loading={loading}
          disabled={mode === "text" ? value.trim().length === 0 : referenceFiles.length === 0}
          onClick={onSearch}
        >
          Search
        </Button>

        {searchActive && (
          <Button size="md" variant="light" color="gray" onClick={onClear}>
            Clear
          </Button>
        )}
      </Group>
    </Stack>
  );
}
