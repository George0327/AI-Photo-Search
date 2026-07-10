package com.photosearch.search.drive;

/** Builds the display name "EventName_originalFilename" (album title stripped to filename-safe chars, e.g. "JobShop2026_DSC_1234.jpg"). Display-only; mirrors the indexing-service helper. */
public final class PhotoNaming {

    private PhotoNaming() {}

    public static String displayName(String albumTitle, String filename) {
        String name = filename != null ? filename : "";
        if (albumTitle == null || albumTitle.isBlank()) return name;
        String prefix = albumTitle.replaceAll("[^A-Za-z0-9._-]", "");
        return prefix.isEmpty() ? name : prefix + "_" + name;
    }
}
