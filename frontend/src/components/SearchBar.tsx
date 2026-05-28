import { useRef } from "react";
import { Button, Group, Kbd, Loader, Select, TextInput } from "@mantine/core";
import { useHotkeys } from "@mantine/hooks";
import { IconSearch } from "@tabler/icons-react";

export const TOP_K_OPTIONS = ["10", "20", "50", "100"];

type SearchBarProps = {
  value: string;
  topK: string;
  loading: boolean;
  searchMode: boolean;
  onChange: (value: string) => void;
  onTopKChange: (value: string) => void;
  onSearch: () => void;
  onClear: () => void;
};

export function SearchBar({
  value,
  topK,
  loading,
  searchMode,
  onChange,
  onTopKChange,
  onSearch,
  onClear,
}: SearchBarProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  useHotkeys([["slash", () => inputRef.current?.focus()]]);

  return (
    <Group align="end" gap="sm">
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
      <Select
        size="md"
        radius="md"
        value={topK}
        onChange={(val) => onTopKChange(val ?? "20")}
        data={TOP_K_OPTIONS}
        w={90}
      />
      {searchMode && (
        <Button size="md" variant="light" color="gray" onClick={onClear}>
          Clear
        </Button>
      )}
    </Group>
  );
}
