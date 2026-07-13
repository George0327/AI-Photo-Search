import { useEffect, useRef, useState } from "react";
import { notifications } from "@mantine/notifications";
import {
  fetchAlbumIndexStatuses,
  syncAndFetchAlbumStatuses,
  indexAlbum,
  indexAllNewPhotos,
  indexFaces,
  reindexAlbum,
  reindexFaces,
  stopIndexAlbum,
  stopIndexFaces,
} from "../api/adminAiApi";
import type { AlbumIndexStatus, DriveIndexFailure, DriveIndexResult } from "../api/adminAiApi";
import { ADMIN_POLL_INTERVAL_MS, ADMIN_POLL_MAX_INTERVAL_MS } from "../constants";

export function useAdminIndexing() {
  const [albums, setAlbums] = useState<AlbumIndexStatus[]>([]);
  const [albumsLoading, setAlbumsLoading] = useState(false);

  const [indexingAlbumId, setIndexingAlbumId] = useState<string | null>(null);
  const [reindexingAlbumId, setReindexingAlbumId] = useState<string | null>(null);
  const [indexingAll, setIndexingAll] = useState(false);
  const [indexingFacesAlbumId, setIndexingFacesAlbumId] = useState<string | null>(null);
  const [reindexingFacesAlbumId, setReindexingFacesAlbumId] = useState<string | null>(null);

  // the album whose stop request is in flight, so the button can show a spinner
  const [stoppingClipId, setStoppingClipId] = useState<string | null>(null);
  const [stoppingFacesId, setStoppingFacesId] = useState<string | null>(null);

  const [failures, setFailures] = useState<DriveIndexFailure[]>([]);
  const [error, setError] = useState<string | null>(null);

  // a ref, not state: the restore effect below would otherwise depend on itself
  const operationInFlight = useRef(false);

  const anyAlbumBusy =
    indexingAlbumId !== null ||
    reindexingAlbumId !== null ||
    indexingAll ||
    indexingFacesAlbumId !== null ||
    reindexingFacesAlbumId !== null;

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

      // a long run can outlive the request while still finishing on the server
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
      "photo",
      `Indexing faces in new photos of "${album.title}"…`,
    );
  }

  function handleReindexFaces(album: AlbumIndexStatus) {
    return runOperation(
      () => setReindexingFacesAlbumId(album.driveAlbumId),
      () => setReindexingFacesAlbumId(null),
      () => reindexFaces(album.driveAlbumId),
      "Face reindex complete",
      "photo",
      `Reindexing faces in all photos of "${album.title}"…`,
    );
  }

  // fire-and-forget: the backend stops between chunks, and polling clears the flag afterwards
  async function handleStopIndex(album: AlbumIndexStatus) {
    setStoppingClipId(album.driveAlbumId);

    try {
      await stopIndexAlbum(album.driveAlbumId);

      notifications.show({
        title: "Stopping…",
        message: `Stopping indexing of "${album.title}". Photos already indexed are kept.`,
        color: "orange",
        autoClose: 5000,
      });
    } catch (err) {
      setStoppingClipId(null);

      notifications.show({
        title: "Could not stop",
        message: err instanceof Error ? err.message : "Failed to request stop.",
        color: "red",
        autoClose: 5000,
      });
    }
  }

  async function handleStopFaces(album: AlbumIndexStatus) {
    setStoppingFacesId(album.driveAlbumId);

    try {
      await stopIndexFaces(album.driveAlbumId);

      notifications.show({
        title: "Stopping…",
        message: `Stopping face indexing of "${album.title}". Photos already indexed are kept.`,
        color: "orange",
        autoClose: 5000,
      });
    } catch (err) {
      setStoppingFacesId(null);

      notifications.show({
        title: "Could not stop",
        message: err instanceof Error ? err.message : "Failed to request stop.",
        color: "red",
        autoClose: 5000,
      });
    }
  }

  useEffect(() => {
    loadAlbumStatuses();
  }, []);

  // picks a run back up when the albums load, so a refresh does not lose it
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

    // the run has wound down, so drop the spinner
    if (!activeClip && stoppingClipId !== null) setStoppingClipId(null);
    if (!activeFaces && stoppingFacesId !== null) setStoppingFacesId(null);
  }, [albums]);

  // poll while anything is indexing, here or in another tab.
  // the interval backs off while the counts stand still and resets as soon as they move
  const shouldPoll = anyAlbumBusy || albums.some(a => a.indexingImages || a.indexingFaces);

  useEffect(() => {
    if (!shouldPoll) return;

    let delay = ADMIN_POLL_INTERVAL_MS;
    let prevSignature = "";
    let timer: ReturnType<typeof setTimeout>;

    const statusSignature = (data: AlbumIndexStatus[]) =>
      data
        .map(a => `${a.indexedImages}:${a.faceIndexedImages}:${a.indexingImages}:${a.indexingFaces}`)
        .join("|");

    const tick = async () => {
      try {
        const data = await fetchAlbumIndexStatuses();
        setAlbums(data);

        const signature = statusSignature(data);

        delay = signature !== prevSignature
          ? ADMIN_POLL_INTERVAL_MS
          : Math.min(delay * 1.5, ADMIN_POLL_MAX_INTERVAL_MS);

        prevSignature = signature;
      } catch (err) {
        console.warn("Polling album statuses failed:", err);
      }

      timer = setTimeout(() => { if (!document.hidden) tick(); }, delay);
    };

    timer = setTimeout(() => { if (!document.hidden) tick(); }, delay);

    // a hidden tab does not poll, so catch up as soon as it comes back
    const onVisibilityChange = () => {
      if (!document.hidden) {
        delay = ADMIN_POLL_INTERVAL_MS;
        clearTimeout(timer);
        tick();
      }
    };

    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [shouldPoll]);

  return {
    albums,
    albumsLoading,
    indexingAlbumId,
    reindexingAlbumId,
    indexingAll,
    indexingFacesAlbumId,
    reindexingFacesAlbumId,
    stoppingClipId,
    stoppingFacesId,
    anyAlbumBusy,
    failures,
    error,
    syncAlbumStatuses,
    handleIndexAlbum,
    handleReindexAlbum,
    handleIndexAllNewPhotos,
    handleIndexFaces,
    handleReindexFaces,
    handleStopIndex,
    handleStopFaces,
  };
}
