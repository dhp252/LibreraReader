package com.foobnix.model;

import android.os.Environment;

import com.foobnix.pdf.info.ExtUtils;

import java.io.File;

public class MyPath {

    final public static String INTERNAL_ROOT = internalRoot();
    final public static String INTERNAL_PREFIX = "internal-storage:";

    // Off a device (JVM unit tests, where android.jar returns null) the usual primary storage path
    // stands in, so the class still loads.
    private static String internalRoot() {
        File dir = Environment.getExternalStorageDirectory();
        return dir != null ? dir.getPath() : "/storage/emulated/0";
    }

    private String path;

    public static MyPath InternalStorate() {
        return new MyPath(Environment.getExternalStorageDirectory());
    }


    public MyPath(File file) {
        this(file.getPath());
    }

    public MyPath(String path) {
        this.path = toRelative(path);
    }

    public String getPath() {
        return toAbsolute(path);
    }

    public String getPathRelative() {
        return path;
    }

    public static String toRelative(String path) {
        if (path == null) {
            return path;
        }
        return path.replace(INTERNAL_ROOT, INTERNAL_PREFIX);
    }

    public static String toAbsolute(String path) {
        if (path == null) {
            return path;
        }
        return path.replace(INTERNAL_PREFIX, INTERNAL_ROOT);
    }

    public static String getSyncPath(String path) {
        if (path == null) {
            return null;
        }
        final File syncBook = new File(AppProfile.SYNC_FOLDER_BOOKS, ExtUtils.getFileName(path));
        return syncBook.isFile() ? syncBook.getPath() : path;
    }


    public interface RelativePath {
        String getPath();

        void setPath(String path);
    }
}
