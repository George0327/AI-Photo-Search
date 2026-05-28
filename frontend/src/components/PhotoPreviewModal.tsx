import { Button, Group, Image, Modal, Stack } from "@mantine/core";
import { IconDownload } from "@tabler/icons-react";
import type { DriveFileInfo } from "../api/driveApi";
import {
  getImageContentUrl,
  getImageDownloadUrl,
} from "../api/driveApi";

type PhotoPreviewModalProps = {
  image: DriveFileInfo | null;
  onClose: () => void;
};

export function PhotoPreviewModal({ image, onClose }: PhotoPreviewModalProps) {
  return (
    <Modal
      opened={image !== null}
      onClose={onClose}
      title={image?.name}
      size="auto"
      centered
    >
      {image && (
        <Stack>
          <Image
            src={getImageContentUrl(image.id)}
            alt={image.name}
            fit="contain"
            mah="75vh"
            radius="sm"
          />
          <Group justify="flex-end">
            <Button
              component="a"
              href={getImageDownloadUrl(image.id)}
              download={image.name}
              leftSection={<IconDownload size={16} />}
            >
              Download Source
            </Button>
          </Group>
        </Stack>
      )}
    </Modal>
  );
}
