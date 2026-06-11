import { BASE_URL, dispatchSessionExpired, extractErrorMessage } from "./apiClient";
import type { DriveFileInfo } from "./driveApi";

export type LogoSearchResult = {
  score: number;
  albumId: string;
  file: DriveFileInfo;
  matchedBbox: number[];
};

export type LogoSearchResponse = {
  message: string;
  results: LogoSearchResult[];
};

export async function searchByLogo(
  referenceFiles: File[],
  albumIds: string[],
  topK: number,
): Promise<LogoSearchResponse> {
  const formData = new FormData();
  for (const file of referenceFiles) {
    formData.append("files", file);
  }
  for (const albumId of albumIds) {
    formData.append("albumIds", albumId);
  }
  formData.append("topK", String(topK));

  const response = await fetch(`${BASE_URL}/api/search/logos`, {
    method: "POST",
    credentials: "include",
    body: formData,
  });

  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }

  if (!response.ok) {
    throw new Error(await extractErrorMessage(response));
  }

  return response.json();
}
