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

const DRIVE_API_BASE = "/api/drive";

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status} ${response.statusText}`);
  }

  const contentType = response.headers.get("content-type");

  if (!contentType || !contentType.includes("application/json")) {
    throw new Error("Backend did not return JSON. You may need to log in first.");
  }

  return response.json();
}

export function fetchAlbums(): Promise<DriveFileInfo[]> {
  return fetchJson<DriveFileInfo[]>(`${DRIVE_API_BASE}/albums`);
}

export function fetchImages(albumId: string): Promise<DriveFileInfo[]> {
  return fetchJson<DriveFileInfo[]>(`${DRIVE_API_BASE}/albums/${albumId}/images`);
}

export function getImageContentUrl(fileId: string): string {
  return `${DRIVE_API_BASE}/images/${fileId}/content`;
}

export function getImageThumbnailUrl(fileId: string): string {
  return `${DRIVE_API_BASE}/images/${fileId}/thumbnail`;
}