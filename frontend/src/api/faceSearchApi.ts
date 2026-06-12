import { postFormData } from "./apiClient";
import type { DriveFileInfo } from "./driveApi";

export type FaceSearchResult = {
  score: number;
  albumId: string;
  file: DriveFileInfo;
  matchedBbox: number[];
};

export type FaceSearchResponse = {
  message: string;
  results: FaceSearchResult[];
};

export async function searchByFace(
  referenceFiles: File[],
  albumIds: string[],
  topK: number,
): Promise<FaceSearchResponse> {
  const formData = new FormData();
  for (const file of referenceFiles) {
    formData.append("files", file);
  }
  for (const albumId of albumIds) {
    formData.append("albumIds", albumId);
  }
  formData.append("topK", String(topK));

  return postFormData<FaceSearchResponse>("/api/search/faces", formData);
}
