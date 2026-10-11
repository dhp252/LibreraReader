package com.foobnix.webdav;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharedSettingsTest {

    /** Shaped like AppState / BookCSS: public fields of simple types. */
    public static class Sample {
        public int fontSizeSp = 20;
        public float lineHeight = 1.2f;
        public boolean isNight = false;
        public String theme = "light";
        public long savedAt = 5;
        public String displayPath = "/storage/emulated/0/Download";
        public int appBrightness = 40;
        public int hashCode = 77;
        public transient int cache = 1;
        public java.util.List<String> list;
        private int secret = 3;
    }

    @Test
    public void deviceThingsAreNotShared() {
        assertTrue(SharedSettings.isDeviceLocal("displayPath"));
        assertTrue(SharedSettings.isDeviceLocal("searchPathsJson"));
        assertTrue(SharedSettings.isDeviceLocal("fontFolder"));
        assertTrue(SharedSettings.isDeviceLocal("appBrightnessNight"));
        assertTrue(SharedSettings.isDeviceLocal("isAllowMinBrigthness"));
        assertTrue(SharedSettings.isDeviceLocal("hashCode"));
        assertTrue(SharedSettings.isDeviceLocal("orientation"));
        assertFalse(SharedSettings.isDeviceLocal("fontSizeSp"));
        assertFalse(SharedSettings.isDeviceLocal("pageTurnStyle"));
        assertFalse(SharedSettings.isDeviceLocal("tapZoneLeft"));
        // display preferences that only mention folders are shared
        assertFalse(SharedSettings.isDeviceLocal("isFolderPreview"));
        assertFalse(SharedSettings.isDeviceLocal("isShowFavFolders"));
    }

    @Test
    public void onlySharedSimpleFieldsAreReadAsText() throws Exception {
        final Map<String, String> fields = SharedSettings.fieldsOf(new Sample());
        final Map<String, String> expected = new HashMap<>();
        expected.put("fontSizeSp", "20");
        expected.put("lineHeight", "1.2");
        expected.put("isNight", "false");
        expected.put("theme", "light");
        expected.put("savedAt", "5");
        assertEquals(expected, fields);
    }

    @Test
    public void valuesFromAnotherDeviceAreSetByTypeAndBadOnesSkipped() {
        final Sample s = new Sample();
        final Map<String, String> values = new HashMap<>();
        values.put("fontSizeSp", "24");
        values.put("lineHeight", "1.5");
        values.put("isNight", "true");
        values.put("theme", "dark");
        values.put("savedAt", "not a number");
        values.put("displayPath", "/other/device");
        values.put("unknownSetting", "x");
        SharedSettings.apply(s, values);

        assertEquals(24, s.fontSizeSp);
        assertEquals(1.5f, s.lineHeight, 1e-6);
        assertTrue(s.isNight);
        assertEquals("dark", s.theme);
        assertEquals(5, s.savedAt);
        assertEquals("/storage/emulated/0/Download", s.displayPath);
    }
}
