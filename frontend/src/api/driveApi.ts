export type DriveFileInfo = {
  id: string;
  name: string;
  mimeType: string;
  parentId: string | null;
  createdTime: string | null;
  modifiedTime: string | null;
  trashed: boolean;
  folder: boolean;
};

import { fetchJson } from "./apiClient";

const DRIVE_API_BASE = "/api/drive";

export function fetchAlbums(): Promise<DriveFileInfo[]> {
  return fetchJson<DriveFileInfo[]>(`${DRIVE_API_BASE}/albums`);
}

export function fetchImages(albumId: string): Promise<DriveFileInfo[]> {
  return fetchJson<DriveFileInfo[]>(
    `${DRIVE_API_BASE}/albums/${albumId}/images`,
  );
}

export function getImageContentUrl(fileId: string): string {
  return `${DRIVE_API_BASE}/images/${fileId}/content`;
}

export function getImageThumbnailUrl(fileId: string): string {
  return `${DRIVE_API_BASE}/images/${fileId}/thumbnail`;
}

export function getImageDownloadUrl(fileId: string): string {
  return `${DRIVE_API_BASE}/images/${fileId}/download`;
}
