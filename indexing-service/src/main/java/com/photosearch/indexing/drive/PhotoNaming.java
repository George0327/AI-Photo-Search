package com.photosearch.indexing.drive;

// builds the display name "EventName_originalFilename"; the files in Drive are not renamed
public final class PhotoNaming {

    private PhotoNaming() {}

    public static String displayName(String albumTitle, String filename) {
        String name = filename != null ? filename : "";
        if (albumTitle == null || albumTitle.isBlank()) return name;
        String prefix = albumTitle.replaceAll("[^A-Za-z0-9._-]", "");
        return prefix.isEmpty() ? name : prefix + "_" + name;
    }
}
