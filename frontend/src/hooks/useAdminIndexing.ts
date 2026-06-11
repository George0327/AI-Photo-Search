import { useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import {
  fetchAlbumIndexStatuses,
  syncAndFetchAlbumStatuses,
  indexAlbum,
  indexAllNewPhotos,
  indexFaces,
  indexLogos,
  reindexAlbum,
} from "../api/adminAiApi";
import type { AlbumIndexStatus, DriveIndexFailure, DriveIndexResult } from "../api/adminAiApi";

export function useAdminIndexing() {
  const [albums, setAlbums] = useState<AlbumIndexStatus[]>([]);
  const [albumsLoading, setAlbumsLoading] = useState(false);

  const [indexingAlbumId, setIndexingAlbumId] = useState<string | null>(null);
  const [reindexingAlbumId, setReindexingAlbumId] = useState<string | null>(null);
  const [indexingAll, setIndexingAll] = useState(false);
  const [indexingFacesAlbumId, setIndexingFacesAlbumId] = useState<string | null>(null);
  const [indexingLogosAlbumId, setIndexingLogosAlbumId] = useState<string | null>(null);

  const [failures, setFailures] = useState<DriveIndexFailure[]>([]);
  const [error, setError] = useState<string | null>(null);

  const anyAlbumBusy =
    indexingAlbumId !== null ||
    reindexingAlbumId !== null ||
    indexingAll ||
    indexingFacesAlbumId !== null ||
    indexingLogosAlbumId !== null;

  async function loadAlbumStatuses() {
    try {
      setError(null);
      setAlbumsLoading(true);
      const data = await fetchAlbumIndexStatuses();
      setAlbums(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load album indexing statuses.");
    } finally {
      setAlbumsLoading(false);
    }
  }

  async function syncAlbumStatuses() {
    try {
      setError(null);
      setAlbumsLoading(true);
      const data = await syncAndFetchAlbumStatuses();
      setAlbums(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to sync albums from Drive.");
    } finally {
      setAlbumsLoading(false);
    }
  }

  async function runOperation(
    startLoading: () => void,
    stopLoading: () => void,
    apiCall: () => Promise<DriveIndexResult>,
    successTitle = "Success",
    countLabel = "photo",
  ) {
    setError(null);
    setFailures([]);
    startLoading();
    try {
      const data = await apiCall();
      const n = data.newlyIndexedCount;
      notifications.show({
        title: data.failedCount === 0 ? successTitle : "Completed with errors",
        message: `${n} ${countLabel}${n !== 1 ? "s" : ""} indexed.${data.failedCount > 0 ? ` ${data.failedCount} failed.` : ""}`,
        color: data.failedCount === 0 ? "green" : "yellow",
        autoClose: 5000,
      });
      if (data.failures.length > 0) setFailures(data.failures);
      await loadAlbumStatuses();
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      const isTimeout =
        msg.includes("504") ||
        msg.includes("502") ||
        msg.toLowerCase().includes("failed to fetch");

      if (isTimeout) {
        notifications.show({
          title: "Connection timed out",
          message: "The operation may have completed in the background. Refreshing status...",
          color: "yellow",
          autoClose: 8000,
        });
        await loadAlbumStatuses();
      } else {
        setError(msg || "Operation failed.");
      }
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

  function handleIndexFaces(album: AlbumIndexStatus) {
    return runOperation(
      () => setIndexingFacesAlbumId(album.driveAlbumId),
      () => setIndexingFacesAlbumId(null),
      () => indexFaces(album.driveAlbumId),
      "Faces indexed",
      "face",
    );
  }

  function handleIndexLogos(album: AlbumIndexStatus) {
    return runOperation(
      () => setIndexingLogosAlbumId(album.driveAlbumId),
      () => setIndexingLogosAlbumId(null),
      () => indexLogos(album.driveAlbumId),
      "Logos indexed",
      "logo region",
    );
  }

  useEffect(() => {
    loadAlbumStatuses();
  }, []);

  // Restore in-progress indexing state when albums load (e.g. after navigation)
  useEffect(() => {
    const activeAlbum = albums.find(a => a.indexingImages);
    if (activeAlbum && indexingAlbumId === null) {
      setIndexingAlbumId(activeAlbum.driveAlbumId);
    } else if (!activeAlbum && indexingAlbumId !== null && !anyAlbumBusy) {
      setIndexingAlbumId(null);
    }
  }, [albums]);

  // Poll every 3 seconds while any album is actively indexing
  useEffect(() => {
    const isPolling = albums.some(a => a.indexingImages);
    if (!isPolling) return;

    const interval = setInterval(async () => {
      try {
        const data = await fetchAlbumIndexStatuses();
        setAlbums(data);
      } catch {
        // ignore polling errors
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [albums]);

  return {
    albums,
    albumsLoading,
    indexingAlbumId,
    reindexingAlbumId,
    indexingAll,
    indexingFacesAlbumId,
    indexingLogosAlbumId,
    anyAlbumBusy,
    failures,
    error,
    loadAlbumStatuses,
    syncAlbumStatuses,
    handleIndexAlbum,
    handleReindexAlbum,
    handleIndexAllNewPhotos,
    handleIndexFaces,
    handleIndexLogos,
  };
}
