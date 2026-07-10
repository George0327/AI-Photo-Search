package com.photosearch.indexing.ai.indexing;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Cooperative stop for indexing runs (they block on allOf().join() and can't be interrupted): a run registers a key per album+kind, each chunk checks it, a stop request flips the flag; in-flight chunks finish, queued ones bail, so the run is resumable. */
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
