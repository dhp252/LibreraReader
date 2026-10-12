package com.foobnix.model;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileMigrationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(File folder, String path, String content, long modified) throws IOException {
        final File file = new File(folder, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        file.setLastModified(modified);
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void everyDeviceFolderIsJoinedIntoTheProfileFolder() throws Exception {
        final File profile = tmp.newFolder("profile.Librera");
        final File phone = new File(profile, "device.Phone");
        final File tablet = new File(profile, "device.Tab");
        write(phone, "app-Progress.json", "{\"a.pdf\":{\"p\":0.5,\"t\":200}}", 1000);
        write(tablet, "app-Progress.json", "{\"a.pdf\":{\"p\":0.2,\"t\":100},\"b.pdf\":{\"p\":0.9,\"t\":100}}", 1000);
        write(phone, "app-State.json", "{\"phone\":true}", 1000);
        write(tablet, "app-State.json", "{\"tablet\":true}", 1000);
        write(tablet, "ai/a.pdf.json", "TABLET", 5000);
        write(phone, "ai/a.pdf.json", "PHONE", 1000);

        final List<String> migrated = ProfileMigration.migrate(profile, "device.Phone");

        assertEquals(2, migrated.size());
        final JSONObject progress = new JSONObject(read(new File(profile, "app-Progress.json")));
        assertEquals(0.5, progress.getJSONObject("a.pdf").getDouble("p"), 0);   // the most recent read
        assertTrue(progress.has("b.pdf"));
        // this device's settings only, and out of the synced folder
        assertEquals("{\"phone\":true}", read(new File(profile, ".device/app-State.json")));
        assertFalse(new File(profile, "app-State.json").exists());
        // other files: the newer copy
        assertEquals("TABLET", read(new File(profile, "ai/a.pdf.json")));
        // old folders kept hidden, and listed for the server
        assertFalse(phone.exists());
        assertTrue(new File(profile, ".migrated-device.Phone/app-Progress.json").isFile());
        assertEquals(Arrays.asList("device.Tab", "device.Phone"), ProfileMigration.pendingOnServer(profile));
    }

    @Test
    public void nothingToDoWithoutDeviceFolders() throws Exception {
        final File profile = tmp.newFolder("profile.Librera");
        write(profile, "app-Progress.json", "{}", 1000);

        assertEquals(Collections.emptyList(), ProfileMigration.migrate(profile, "device.Phone"));
        assertEquals(Collections.emptyList(), ProfileMigration.pendingOnServer(profile));
    }

    @Test
    public void aFolderDeletedOnTheServerIsForgotten() throws Exception {
        final File profile = tmp.newFolder("profile.Librera");
        write(new File(profile, "device.Phone"), "app-Recent.json", "[]", 1000);
        ProfileMigration.migrate(profile, "device.Phone");

        ProfileMigration.deletedOnServer(profile, "device.Phone");

        assertEquals(Collections.emptyList(), ProfileMigration.pendingOnServer(profile));
    }
}
