import { fetchJson } from "./apiClient";
import type { DriveFileInfo } from "./driveApi";

export type PhotoSearchResult = {
  score: number;
  albumId: string;
  file: DriveFileInfo;
};

export type PhotoSearchResponse = {
  query: string;
  topK: number;
  albumIds: string[];
  results: PhotoSearchResult[];
};

export function searchPhotos(
  query: string,
  topK = 20,
  albumIds: string[] = [],
): Promise<PhotoSearchResponse> {
  const params = new URLSearchParams({ query, topK: String(topK) });
  albumIds.forEach((albumId) => params.append("albumIds", albumId));
  return fetchJson<PhotoSearchResponse>(`/api/search?${params.toString()}`);
}
