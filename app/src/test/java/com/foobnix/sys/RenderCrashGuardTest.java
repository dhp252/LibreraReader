package com.foobnix.sys;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RenderCrashGuardTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void markIsSeenUntilUnmarked() {
        final RenderCrashGuard guard = new RenderCrashGuard(new File(tmp.getRoot(), "render-crash"));
        assertFalse(guard.isMarked("-123"));

        assertTrue(guard.mark("-123"));
        assertTrue(guard.isMarked("-123"));
        assertFalse(guard.isMarked("456"));

        guard.unmark("-123");
        assertFalse(guard.isMarked("-123"));
    }

    @Test
    public void markLeftByADeadProcessIsSeenByTheNextOne() {
        final File dir = new File(tmp.getRoot(), "render-crash");
        new RenderCrashGuard(dir).mark("789");

        assertTrue(new RenderCrashGuard(dir).isMarked("789"));
    }

    @Test
    public void clearForgetsEveryMark() {
        final RenderCrashGuard guard = new RenderCrashGuard(new File(tmp.getRoot(), "render-crash"));
        guard.mark("1");
        guard.mark("2");

        guard.clear();

        assertFalse(guard.isMarked("1"));
        assertFalse(guard.isMarked("2"));
    }

    @Test
    public void clearWithoutFolderDoesNothing() {
        new RenderCrashGuard(new File(tmp.getRoot(), "missing")).clear();
    }

    @Test
    public void markFailsWhenFolderCannotBeMade() throws Exception {
        final File file = tmp.newFile("not-a-folder");

        assertFalse(new RenderCrashGuard(file).mark("1"));
    }
}
