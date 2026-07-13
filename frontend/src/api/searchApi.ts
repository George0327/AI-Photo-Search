import { fetchJson, postJsonBody } from "./apiClient";
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
  restrictToIds?: string[],
): Promise<PhotoSearchResponse> {
  // GET so a plain search can be bookmarked, POST when the id list is too long for a URL
  if (restrictToIds && restrictToIds.length > 0) {
    return postJsonBody<PhotoSearchResponse>("/api/search", {
      query,
      topK,
      albumIds,
      restrictToIds,
    });
  }
  const params = new URLSearchParams({ query, topK: String(topK) });
  albumIds.forEach((albumId) => params.append("albumIds", albumId));
  return fetchJson<PhotoSearchResponse>(`/api/search?${params.toString()}`);
}
