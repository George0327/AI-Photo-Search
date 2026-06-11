import { fetchJson, postJson } from "./apiClient";

export type AlbumIndexStatus = {
  albumDatabaseId: number;
  driveAlbumId: string;
  title: string;
  year?: string;
  totalActiveImages: number;
  indexedImages: number;
  needsIndexImages: number;
  deletedImages: number;
  reindexRecommended: boolean;
  indexingImages: boolean;
  indexingFaces: boolean;
  faceIndexedAt: string | null;
};

export type DriveIndexFailure = {
  fileId: string;
  fileName: string;
  error: string;
};

export type DriveIndexResult = {
  albumCount: number;
  totalActiveImages: number;
  indexedImages: number;
  needsIndexImages: number;
  deletedImages: number;
  newlyIndexedCount: number;
  failedCount: number;
  failures: DriveIndexFailure[];
};

export function fetchAlbumIndexStatuses(): Promise<AlbumIndexStatus[]> {
  return fetchJson<AlbumIndexStatus[]>("/api/admin/ai/albums/status");
}

export function syncAndFetchAlbumStatuses(): Promise<AlbumIndexStatus[]> {
  return postJson<AlbumIndexStatus[]>("/api/admin/ai/albums/sync");
}

export function indexAlbum(albumId: string): Promise<DriveIndexResult> {
  return postJson<DriveIndexResult>(`/api/admin/ai/index-album/${albumId}`);
}

export function reindexAlbum(albumId: string): Promise<DriveIndexResult> {
  return postJson<DriveIndexResult>(`/api/admin/ai/reindex-album/${albumId}`);
}

export function indexAllNewPhotos(): Promise<DriveIndexResult> {
  return postJson<DriveIndexResult>("/api/admin/ai/index-all");
}

export function indexFaces(albumId: string): Promise<DriveIndexResult> {
  return postJson<DriveIndexResult>(`/api/admin/ai/index-faces/${albumId}`);
}

