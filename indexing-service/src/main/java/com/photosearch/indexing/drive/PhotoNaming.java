package com.photosearch.indexing.drive;

/**
 * Builds the user-facing photo name "EventName_originalFilename", where EventName is the album
 * title with whitespace and non-filename-safe characters stripped (e.g. "Job Shop 2026" +
 * "DSC_1234.jpg" -> "JobShop2026_DSC_1234.jpg"). Display/download only; the files in Drive are
 * not renamed.
 */
public final class PhotoNaming {

    private PhotoNaming() {}

    public static String displayName(String albumTitle, String filename) {
        String name = filename != null ? filename : "";
        if (albumTitle == null || albumTitle.isBlank()) return name;
        String prefix = albumTitle.replaceAll("[^A-Za-z0-9._-]", "");
        return prefix.isEmpty() ? name : prefix + "_" + name;
    }
}
