import { useEffect, useRef, useState } from "react";
import { ActionIcon, Box, Button, Group, Image, Stack, Text } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { IconPhoto, IconUpload, IconX } from "@tabler/icons-react";
import { MAX_REFERENCE_FILES, MAX_REFERENCE_FILE_SIZE_MB } from "../constants";

type ReferenceImageUploadProps = {
  files: File[];
  onFilesChange: (files: File[]) => void;
  label: string;
  maxFiles?: number;
};

export function ReferenceImageUpload({
  files,
  onFilesChange,
  label,
  maxFiles = MAX_REFERENCE_FILES,
}: ReferenceImageUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [previews, setPreviews] = useState<string[]>([]);

  useEffect(() => {
    const urls = files.map((f) => URL.createObjectURL(f));
    setPreviews(urls);
    return () => urls.forEach((url) => URL.revokeObjectURL(url));
  }, [files]);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(e.target.files ?? []);
    const rejected: string[] = [];
    const accepted = selected.filter((file) => {
      const isImage = file.type.startsWith("image/");
      const fitsSize = file.size <= MAX_REFERENCE_FILE_SIZE_MB * 1024 * 1024;
      if (!isImage || !fitsSize) {
        rejected.push(file.name);
        return false;
      }
      return true;
    });
    if (rejected.length > 0) {
      notifications.show({
        title: "Some files were skipped",
        message: `Only images up to ${MAX_REFERENCE_FILE_SIZE_MB} MB are accepted: ${rejected.join(", ")}`,
        color: "yellow",
      });
    }
    const merged = [...files, ...accepted].slice(0, maxFiles);
    onFilesChange(merged);
    e.target.value = "";
  }

  function removeFile(index: number) {
    onFilesChange(files.filter((_, i) => i !== index));
  }

  return (
    <Box style={{ flex: 1 }}>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        style={{ display: "none" }}
        onChange={handleFileChange}
      />

      {files.length === 0 ? (
        <Button
          variant="default"
          size="md"
          radius="md"
          leftSection={<IconUpload size={16} />}
          onClick={() => inputRef.current?.click()}
          style={{ width: "100%" }}
        >
          Upload {label} (up to {maxFiles} photos)
        </Button>
      ) : (
        <Stack gap="xs">
          <Group gap="xs" wrap="wrap">
            {previews.map((src, i) => (
              <Box key={i} style={{ position: "relative" }}>
                <Image
                  src={src}
                  w={56}
                  h={56}
                  radius="sm"
                  fit="cover"
                  alt={`Reference ${i + 1}`}
                />
                <ActionIcon
                  size="xs"
                  color="red"
                  variant="filled"
                  radius="xl"
                  style={{ position: "absolute", top: -6, right: -6 }}
                  onClick={() => removeFile(i)}
                  aria-label={`Remove reference image ${i + 1}`}
                >
                  <IconX size={10} />
                </ActionIcon>
              </Box>
            ))}
            {files.length < maxFiles && (
              <ActionIcon
                variant="default"
                size={56}
                radius="sm"
                onClick={() => inputRef.current?.click()}
                aria-label="Add more reference images"
              >
                <IconPhoto size={20} />
              </ActionIcon>
            )}
          </Group>
          <Text size="xs" c="dimmed">
            {files.length} reference photo{files.length !== 1 ? "s" : ""} selected
          </Text>
        </Stack>
      )}
    </Box>
  );
}
