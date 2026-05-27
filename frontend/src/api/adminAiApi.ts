export type DriveIndexFailure = {
  fileId: string;
  fileName: string;
  error: string;
};

export type DriveIndexResult = {
  albumCount: number;
  totalImages: number;
  indexedCount: number;
  failedCount: number;
  failures: DriveIndexFailure[];
};

export async function rebuildAlbumIndex(
  albumId: string
): Promise<DriveIndexResult> {
  const response = await fetch(`/api/admin/ai/rebuild-album/${albumId}`, {
    method: "POST",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Album indexing failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

export async function rebuildFullDriveIndex(): Promise<DriveIndexResult> {
  const response = await fetch("/api/admin/ai/rebuild-drive", {
    method: "POST",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Drive indexing failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}