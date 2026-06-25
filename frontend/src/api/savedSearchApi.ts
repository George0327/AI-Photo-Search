import { deleteVoid, fetchJson } from "./apiClient";
import type { DriveFileInfo } from "./driveApi";

export type SavedSearchSummary = {
  id: number;
  searchType: "TEXT" | "FACE";
  queryText: string | null;
  albumIds: string[];
  resultCount: number;
  createdAt: string;
};

export type SavedSearchResults = {
  id: number;
  searchType: "TEXT" | "FACE";
  queryText: string | null;
  results: { albumId: string; file: DriveFileInfo }[];
};

export function fetchSavedSearches(): Promise<SavedSearchSummary[]> {
  return fetchJson<SavedSearchSummary[]>("/api/search/saved");
}

export function recallSavedSearch(id: number): Promise<SavedSearchResults> {
  return fetchJson<SavedSearchResults>(`/api/search/saved/${id}`);
}

export function clearSavedSearches(): Promise<void> {
  return deleteVoid("/api/search/saved");
}
