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
  // Plain search uses GET (bookmarkable); "search within results" uses POST so the large restrictToIds list isn't crammed into the URL.
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
