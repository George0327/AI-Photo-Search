import { useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import {
  fetchAlbumIndexStatuses,
  indexAlbum,
  indexAllNewPhotos,
  reindexAlbum,
} from "../api/adminAiApi";
import type { AlbumIndexStatus, DriveIndexFailure, DriveIndexResult } from "../api/adminAiApi";

export function useAdminIndexing() {
  const [albums, setAlbums] = useState<AlbumIndexStatus[]>([]);
  const [albumsLoading, setAlbumsLoading] = useState(false);

  const [indexingAlbumId, setIndexingAlbumId] = useState<string | null>(null);
  const [reindexingAlbumId, setReindexingAlbumId] = useState<string | null>(null);
  const [indexingAll, setIndexingAll] = useState(false);

  const [failures, setFailures] = useState<DriveIndexFailure[]>([]);
  const [error, setError] = useState<string | null>(null);

  const anyAlbumBusy =
    indexingAlbumId !== null || reindexingAlbumId !== null || indexingAll;

  async function loadAlbumStatuses() {
    try {
      setError(null);
      setAlbumsLoading(true);
      const data = await fetchAlbumIndexStatuses();
      setAlbums(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load album indexing statuses.",
      );
    } finally {
      setAlbumsLoading(false);
    }
  }

  async function runOperation(
    startLoading: () => void,
    stopLoading: () => void,
    apiCall: () => Promise<DriveIndexResult>,
  ) {
    setError(null);
    setFailures([]);
    startLoading();
    try {
      const data = await apiCall();
      notifications.show({
        title: data.failedCount === 0 ? "Success" : "Completed with errors",
        message: `${data.newlyIndexedCount} photo${data.newlyIndexedCount !== 1 ? "s" : ""} newly indexed.${data.failedCount > 0 ? ` ${data.failedCount} failed.` : ""}`,
        color: data.failedCount === 0 ? "green" : "yellow",
        autoClose: 5000,
      });
      if (data.failures.length > 0) setFailures(data.failures);
      await loadAlbumStatuses();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Operation failed.");
    } finally {
      stopLoading();
    }
  }

  function handleIndexAlbum(album: AlbumIndexStatus) {
    return runOperation(
      () => setIndexingAlbumId(album.driveAlbumId),
      () => setIndexingAlbumId(null),
      () => indexAlbum(album.driveAlbumId),
    );
  }

  function handleReindexAlbum(album: AlbumIndexStatus) {
    return runOperation(
      () => setReindexingAlbumId(album.driveAlbumId),
      () => setReindexingAlbumId(null),
      () => reindexAlbum(album.driveAlbumId),
    );
  }

  function handleIndexAllNewPhotos() {
    return runOperation(
      () => setIndexingAll(true),
      () => setIndexingAll(false),
      indexAllNewPhotos,
    );
  }

  useEffect(() => {
    loadAlbumStatuses();
  }, []);

  return {
    albums,
    albumsLoading,
    indexingAlbumId,
    reindexingAlbumId,
    indexingAll,
    anyAlbumBusy,
    failures,
    error,
    loadAlbumStatuses,
    handleIndexAlbum,
    handleReindexAlbum,
    handleIndexAllNewPhotos,
  };
}
