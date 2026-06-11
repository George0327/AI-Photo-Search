import { useEffect, useRef, useState } from "react";
import { notifications } from "@mantine/notifications";
import {
  fetchAlbumIndexStatuses,
  syncAndFetchAlbumStatuses,
  indexAlbum,
  indexAllNewPhotos,
  indexFaces,
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

  const [failures, setFailures] = useState<DriveIndexFailure[]>([]);
  const [error, setError] = useState<string | null>(null);

  // Tracks whether an HTTP indexing request is currently in flight.
  // Used by the restore effect to avoid a circular dependency with anyAlbumBusy.
  const operationInFlight = useRef(false);

  const anyAlbumBusy =
    indexingAlbumId !== null ||
    reindexingAlbumId !== null ||
    indexingAll ||
    indexingFacesAlbumId !== null;

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
    startMessage?: string,
  ) {
    setError(null);
    setFailures([]);
    if (startMessage) {
      notifications.show({
        title: "Started",
        message: startMessage,
        color: "blue",
        autoClose: 3000,
      });
    }
    operationInFlight.current = true;
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
      operationInFlight.current = false;
      stopLoading();
    }
  }

  function handleIndexAlbum(album: AlbumIndexStatus) {
    return runOperation(
      () => setIndexingAlbumId(album.driveAlbumId),
      () => setIndexingAlbumId(null),
      () => indexAlbum(album.driveAlbumId),
      "Indexing complete",
      "photo",
      `Indexing new photos in "${album.title}"…`,
    );
  }

  function handleReindexAlbum(album: AlbumIndexStatus) {
    return runOperation(
      () => setReindexingAlbumId(album.driveAlbumId),
      () => setReindexingAlbumId(null),
      () => reindexAlbum(album.driveAlbumId),
      "Reindex complete",
      "photo",
      `Reindexing all photos in "${album.title}"…`,
    );
  }

  function handleIndexAllNewPhotos() {
    return runOperation(
      () => setIndexingAll(true),
      () => setIndexingAll(false),
      indexAllNewPhotos,
      "Indexing complete",
      "photo",
      "Indexing new photos across all albums…",
    );
  }

  function handleIndexFaces(album: AlbumIndexStatus) {
    return runOperation(
      () => setIndexingFacesAlbumId(album.driveAlbumId),
      () => setIndexingFacesAlbumId(null),
      () => indexFaces(album.driveAlbumId),
      "Faces indexed",
      "face",
      `Indexing faces in "${album.title}"…`,
    );
  }

  useEffect(() => {
    loadAlbumStatuses();
  }, []);

  // Restore in-progress indexing state when albums load (e.g. after navigation or page refresh).
  // Uses operationInFlight ref (not anyAlbumBusy) to avoid a circular dependency where
  // anyAlbumBusy is always true when indexingAlbumId !== null.
  useEffect(() => {
    const activeClip  = albums.find(a => a.indexingImages);
    const activeFaces = albums.find(a => a.indexingFaces);

    if (activeClip && indexingAlbumId === null)
      setIndexingAlbumId(activeClip.driveAlbumId);
    else if (!activeClip && indexingAlbumId !== null && !operationInFlight.current)
      setIndexingAlbumId(null);

    if (activeFaces && indexingFacesAlbumId === null)
      setIndexingFacesAlbumId(activeFaces.driveAlbumId);
    else if (!activeFaces && indexingFacesAlbumId !== null && !operationInFlight.current)
      setIndexingFacesAlbumId(null);
  }, [albums]);

  // Poll every 3 seconds while any album is actively indexing (CLIP, faces, or logos)
  useEffect(() => {
    const isPolling = albums.some(a => a.indexingImages || a.indexingFaces);
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
    anyAlbumBusy,
    failures,
    error,
    loadAlbumStatuses,
    syncAlbumStatuses,
    handleIndexAlbum,
    handleReindexAlbum,
    handleIndexAllNewPhotos,
    handleIndexFaces,
  };
}
