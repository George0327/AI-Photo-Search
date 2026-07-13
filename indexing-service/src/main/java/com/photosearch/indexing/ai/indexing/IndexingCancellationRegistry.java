package com.photosearch.indexing.ai.indexing;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// cooperative stop for indexing runs: a run registers a key per album+kind and every chunk checks it.
// in-flight chunks finish, queued ones bail, so a stopped run stays resumable
@Component
public class IndexingCancellationRegistry {

    public enum Kind { CLIP, FACE }

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    private static String key(Kind kind, String albumId) {
        return kind + ":" + albumId;
    }

    public void begin(Kind kind, String albumId) {
        cancelled.remove(key(kind, albumId));
    }

    public void requestCancel(Kind kind, String albumId) {
        cancelled.add(key(kind, albumId));
    }

    // checked at the start of each chunk
    public boolean isCancelled(Kind kind, String albumId) {
        return cancelled.contains(key(kind, albumId));
    }

    public void end(Kind kind, String albumId) {
        cancelled.remove(key(kind, albumId));
    }
}
