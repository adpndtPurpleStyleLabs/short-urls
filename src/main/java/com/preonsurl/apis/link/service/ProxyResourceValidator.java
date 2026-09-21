package com.preonsurl.apis.link.service;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public final class ProxyResourceValidator {

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // Archives
            "zip", "tar", "gz", "tgz", "rar", "7z", "bz2", "xz",
            // Documents
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "ods", "odp",
            // Images
            "jpg", "jpeg", "png", "gif", "svg", "webp", "bmp", "ico", "tiff", "tif", "avif",
            // Audio & Video
            "mp4", "webm", "mkv", "avi", "mov", "mp3", "wav", "ogg", "flac", "m4a", "aac", "wmv", "flv",
            // Data, Fonts & Binaries
            "json", "xml", "woff", "woff2", "ttf", "otf", "eot", "bin", "iso", "dmg", "apk"
    );

    public static final String ACCEPTABLE_RESOURCES_MESSAGE =
            "URL cannot be a webpage when proxy mode is selected. Acceptable resources include: .zip, .pdf, .jpg, .jpeg, .png, .gif, .svg, .webp, .mp4, .mp3, .wav, .csv, .json, .xml, .txt, .tar, .gz, .docx, .xlsx, .pptx";

    private ProxyResourceValidator() {
    }

    public static void validateProxyUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        String path = extractPath(url.trim());
        if (path == null || path.isBlank() || path.endsWith("/")) {
            throw new IllegalArgumentException(ACCEPTABLE_RESOURCES_MESSAGE);
        }

        int lastSlash = path.lastIndexOf('/');
        String filename = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException(ACCEPTABLE_RESOURCES_MESSAGE);
        }

        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(ACCEPTABLE_RESOURCES_MESSAGE);
        }
    }

    public static boolean isAcceptableProxyUrl(String url) {
        try {
            validateProxyUrl(url);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String extractPath(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null) {
                return path;
            }
        } catch (Exception ignored) {
        }

        // Fallback: strip scheme, host, query params, and fragments manually
        int queryIdx = url.indexOf('?');
        String cleanUrl = (queryIdx >= 0) ? url.substring(0, queryIdx) : url;
        int fragmentIdx = cleanUrl.indexOf('#');
        cleanUrl = (fragmentIdx >= 0) ? cleanUrl.substring(0, fragmentIdx) : cleanUrl;

        int doubleSlashIdx = cleanUrl.indexOf("://");
        if (doubleSlashIdx >= 0) {
            int pathStart = cleanUrl.indexOf('/', doubleSlashIdx + 3);
            return (pathStart >= 0) ? cleanUrl.substring(pathStart) : "";
        }
        return cleanUrl;
    }
}
