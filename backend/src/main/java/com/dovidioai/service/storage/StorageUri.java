package com.dovidioai.service.storage;

public final class StorageUri {

    public static final String OSS_PREFIX = "oss:";

    private StorageUri() {
    }

    public static boolean isOss(String path) {
        return path != null && path.startsWith(OSS_PREFIX);
    }

    public static String toOss(String objectKey) {
        return OSS_PREFIX + objectKey;
    }

    public static String fromOss(String uri) {
        if (!isOss(uri)) {
            throw new IllegalArgumentException("Not an OSS URI: " + uri);
        }
        return uri.substring(OSS_PREFIX.length());
    }
}
