package com.foobnix.webdav;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SettingsMergeTest {

    private static Map<String, String> map(String... kv) {
        final Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void changesToDifferentSettingsOnTwoDevicesBothSurvive() {
        final Map<String, String> base = map("font", "20", "theme", "light");
        final Map<String, String> local = map("font", "24", "theme", "light");
        final Map<String, String> remote = map("font", "20", "theme", "dark");

        final SettingsMerge.Result r = SettingsMerge.merge(base, local, remote, false);
        assertEquals(map("font", "24", "theme", "dark"), r.merged);
        assertEquals(Collections.singleton("theme"), r.fromRemote);
    }

    @Test
    public void theSameSettingChangedOnBothSidesTakesTheNewer() {
        final Map<String, String> base = map("font", "20");
        final Map<String, String> local = map("font", "24");
        final Map<String, String> remote = map("font", "18");

        assertEquals("24", SettingsMerge.merge(base, local, remote, true).merged.get("font"));
        final SettingsMerge.Result older = SettingsMerge.merge(base, local, remote, false);
        assertEquals("18", older.merged.get("font"));
        assertEquals(Collections.singleton("font"), older.fromRemote);
    }

    @Test
    public void aDeviceThatNeverMergedTakesTheSharedSettings() {
        // its own defaults are newer on disk, but they must not overwrite the real settings
        final Map<String, String> local = map("font", "20", "theme", "light", "onlyHere", "1");
        final Map<String, String> remote = map("font", "24", "theme", "dark");

        final SettingsMerge.Result r = SettingsMerge.merge(null, local, remote, true);
        assertEquals(map("font", "24", "theme", "dark", "onlyHere", "1"), r.merged);
        assertEquals(new HashSet<>(java.util.Arrays.asList("font", "theme")), r.fromRemote);
    }

    @Test
    public void withNothingOnTheServerTheLocalSettingsGoUp() {
        final Map<String, String> local = map("font", "20");
        final SettingsMerge.Result r = SettingsMerge.merge(null, local, null, false);
        assertEquals(local, r.merged);
        assertTrue(r.fromRemote.isEmpty());
    }

    @Test
    public void settingsOfOtherBuildsAreCarriedButNotLost() {
        final Map<String, String> base = map("font", "20");
        // the server has a setting this build does not know; an older build left one out
        final Map<String, String> local = map("font", "20", "newHere", "x");
        final Map<String, String> remote = map("font", "20", "newThere", "y");

        final SettingsMerge.Result r = SettingsMerge.merge(base, local, remote, false);
        assertEquals("y", r.merged.get("newThere"));
        assertEquals("x", r.merged.get("newHere"));
        assertFalse(r.fromRemote.contains("newHere"));
        assertNull(r.merged.get("missing"));
    }
}
