import type { DriveFileInfo } from "./driveApi";

export type PhotoSearchResult = {
  score: number;
  file: DriveFileInfo;
};

export type PhotoSearchResponse = {
  query: string;
  topK: number;
  results: PhotoSearchResult[];
};

export async function searchPhotos(
  query: string,
  topK = 60
): Promise<PhotoSearchResponse> {
  const params = new URLSearchParams({
    query,
    topK: String(topK),
  });

  const response = await fetch(`/api/search?${params.toString()}`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Search failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}