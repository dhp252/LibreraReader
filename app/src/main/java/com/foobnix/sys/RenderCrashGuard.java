package com.foobnix.sys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Remembers which picture (page or cover) was being made when the app died, so a book that
 * crashes a codec shows "#crash" next time instead of crashing the app again.
 * <p>
 * A mark is an empty file in a folder of its own. Creating and deleting it costs a fraction of a
 * SharedPreferences commit, which rewrote and synced the whole preferences file twice per page,
 * and it still survives a crash of the process: the kernel writes the file out after the app dies.
 */
public final class RenderCrashGuard {

    private final File dir;

    public RenderCrashGuard(File dir) {
        this.dir = dir;
    }

    public boolean isMarked(String key) {
        return new File(dir, key).exists();
    }

    /** @return false when the mark could not be written (the picture is still made) */
    public boolean mark(String key) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return false;
        }
        try {
            new FileOutputStream(new File(dir, key)).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void unmark(String key) {
        new File(dir, key).delete();
    }

    /** Forgets every crash: the books are tried again. */
    public void clear() {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }
}
