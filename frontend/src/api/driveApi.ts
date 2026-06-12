export type DriveFileInfo = {
  id: string;
  name: string;
  mimeType: string;
  parentId: string | null;
  createdTime: string | null;
  modifiedTime: string | null;
  trashed: boolean;
  folder: boolean;
  year?: string;
};

import { BASE_URL, fetchJson } from "./apiClient";

const DRIVE_API_BASE = "/api/drive";

export type AlbumSearchStatus = {
  driveAlbumId: string;
  totalActiveImages: number;
  clipIndexedImages: number;
  clipPendingImages: number;
  faceIndexedImages: number;
  facePendingImages: number;
};

export function fetchAlbums(): Promise<DriveFileInfo[]> {
  return fetchJson<DriveFileInfo[]>(`${DRIVE_API_BASE}/albums`);
}

export function fetchAlbumSearchStatuses(): Promise<AlbumSearchStatus[]> {
  return fetchJson<AlbumSearchStatus[]>(`${DRIVE_API_BASE}/albums/index-status`);
}

export function fetchImages(albumId: string): Promise<DriveFileInfo[]> {
  return fetchJson<DriveFileInfo[]>(
    `${DRIVE_API_BASE}/albums/${albumId}/images`,
  );
}

export function getImageContentUrl(fileId: string): string {
  return `${BASE_URL}${DRIVE_API_BASE}/images/${fileId}/content`;
}

export function getImageThumbnailUrl(fileId: string): string {
  return `${BASE_URL}${DRIVE_API_BASE}/images/${fileId}/thumbnail`;
}

export function getImageDownloadUrl(fileId: string): string {
  return `${BASE_URL}${DRIVE_API_BASE}/images/${fileId}/download`;
}
