package com.photosearch.indexing.ai.indexing;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks admin requests to stop an in-progress indexing run.
 *
 * <p>Indexing runs block their HTTP thread on {@code allOf().join()}, so they
 * cannot be interrupted directly. Cancellation is cooperative instead: the
 * run registers a key with {@link #begin}, each chunk checks {@link #isCancelled}
 * before doing work, and the run clears the key with {@link #end} in a finally
 * block. A separate "stop" request flips the flag via {@link #requestCancel}.
 *
 * <p>Keys are scoped per album and per index kind (CLIP vs faces) so stopping
 * one does not affect the other. In-flight chunks finish their current batch;
 * queued chunks bail before touching any item status, so already-indexed photos
 * are kept and the rest stay unindexed (the run is fully resumable).
 */
@Component
public class IndexingCancellationRegistry {

    public enum Kind { CLIP, FACE }

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    private static String key(Kind kind, String albumId) {
        return kind + ":" + albumId;
    }

    /** Marks a run as starting, clearing any stale cancel flag from a prior request. */
    public void begin(Kind kind, String albumId) {
        cancelled.remove(key(kind, albumId));
    }

    /** Requests that an in-progress run for this album/kind stop as soon as possible. */
    public void requestCancel(Kind kind, String albumId) {
        cancelled.add(key(kind, albumId));
    }

    /** True if a stop has been requested for this run. Checked at the start of each chunk. */
    public boolean isCancelled(Kind kind, String albumId) {
        return cancelled.contains(key(kind, albumId));
    }

    /** Clears the flag once a run has finished (whether it completed or was cancelled). */
    public void end(Kind kind, String albumId) {
        cancelled.remove(key(kind, albumId));
    }
}
