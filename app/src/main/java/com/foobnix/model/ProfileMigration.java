package com.foobnix.model;

import com.foobnix.webdav.ProfileMerge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * From a folder per device to one profile folder (.agents/planning/unified-sync-design.md, D11):
 * every {@code device.*} folder of a profile (this device's and the copies synced from the other
 * devices) is merged into the profile folder itself, item by item for the lists, newer file for
 * the rest; this device's own settings files go to the hidden {@code .device} folder. The old
 * folders are kept, hidden, as a backup ({@code .migrated-device.*}, which no sync touches), and
 * their names are listed in {@code .device/migrated.txt} so the sync deletes them on the server.
 */
public final class ProfileMigration {

    /** Hidden: this device's own files, never synced (settings that hold its folders, brightness...). */
    public static final String DEVICE_FOLDER = ".device";
    public static final String MIGRATED_LIST = "migrated.txt";
    static final String BACKUP_PREFIX = ".migrated-";
    private static final List<String> SETTINGS = Arrays.asList(AppProfile.APP_STATE_JSON, AppProfile.APP_CSS_JSON);

    private ProfileMigration() {
    }

    /**
     * @param ownDevice this device's old folder name ({@code device.<model>})
     * @return the folders migrated now (empty when there were none)
     */
    public static List<String> migrate(File profile, String ownDevice) throws IOException {
        final List<String> migrated = new ArrayList<>();
        final File[] children = profile.listFiles();
        if (children == null) {
            return migrated;
        }
        // this device's folder last: its settings and, on equal times, its files win
        final List<File> devices = new ArrayList<>();
        File own = null;
        for (File child : children) {
            if (child.isDirectory() && child.getName().startsWith(AppProfile.DEVICE_PREFIX)) {
                if (child.getName().equals(ownDevice)) {
                    own = child;
                } else {
                    devices.add(child);
                }
            }
        }
        if (own != null) {
            devices.add(own);
        }
        if (devices.isEmpty()) {
            return migrated;
        }
        final File deviceFolder = new File(profile, DEVICE_FOLDER);
        deviceFolder.mkdirs();
        for (File device : devices) {
            mergeFolder(device, profile, "", device == own, deviceFolder);
        }
        for (File device : devices) {
            File backup = new File(profile, BACKUP_PREFIX + device.getName());
            for (int i = 2; backup.exists(); i++) {
                backup = new File(profile, BACKUP_PREFIX + device.getName() + "-" + i);
            }
            if (!device.renameTo(backup)) {
                throw new IOException("cannot move " + device + " to " + backup);
            }
            migrated.add(device.getName());
        }
        final File list = new File(deviceFolder, MIGRATED_LIST);
        final StringBuilder names = new StringBuilder(list.isFile() ? read(list) : "");
        for (String name : migrated) {
            names.append(name).append('\n');
        }
        write(list, names.toString());
        return migrated;
    }

    /** The old folders still to delete on the server. */
    public static List<String> pendingOnServer(File profile) throws IOException {
        final File list = new File(new File(profile, DEVICE_FOLDER), MIGRATED_LIST);
        final List<String> names = new ArrayList<>();
        if (list.isFile()) {
            for (String line : read(list).split("\n")) {
                if (!line.trim().isEmpty() && !names.contains(line.trim())) {
                    names.add(line.trim());
                }
            }
        }
        return names;
    }

    /** The server no longer has {@code name}: forget it. */
    public static void deletedOnServer(File profile, String name) throws IOException {
        final List<String> names = pendingOnServer(profile);
        names.remove(name);
        final StringBuilder out = new StringBuilder();
        for (String n : names) {
            out.append(n).append('\n');
        }
        write(new File(new File(profile, DEVICE_FOLDER), MIGRATED_LIST), out.toString());
    }

    private static void mergeFolder(File from, File to, String relative, boolean own, File deviceFolder) throws IOException {
        final File[] files = from.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            final String path = relative.isEmpty() ? file.getName() : relative + "/" + file.getName();
            if (file.isDirectory()) {
                mergeFolder(file, to, path, own, deviceFolder);
                continue;
            }
            if (relative.isEmpty() && SETTINGS.contains(file.getName())) {
                // one device's settings stay that device's; the shared part goes through .shared
                final File target = new File(deviceFolder, file.getName());
                if (own && !target.exists()) {
                    copy(file, target);
                }
                continue;
            }
            final File target = new File(to, path);
            if (relative.isEmpty() && ProfileMerge.handles(file.getName())) {
                try {
                    final String merged = ProfileMerge.merge(file.getName(), null,
                            target.isFile() ? read(target) : null, read(file));
                    write(target, merged);
                } catch (Exception e) {
                    // a damaged list is not worth losing the others: the backup keeps it
                    if (!target.exists()) {
                        copy(file, target);
                    }
                }
                continue;
            }
            if (!target.exists() || file.lastModified() > target.lastModified()) {
                copy(file, target);
            }
        }
    }

    private static void copy(File from, File to) throws IOException {
        to.getAbsoluteFile().getParentFile().mkdirs();
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            final byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        to.setLastModified(from.lastModified());
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                final int n = in.read(data, offset, data.length - offset);
                if (n < 0) {
                    break;
                }
                offset += n;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String text) throws IOException {
        file.getAbsoluteFile().getParentFile().mkdirs();
        final File temp = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("cannot replace " + file);
        }
        if (!temp.renameTo(file)) {
            throw new IOException("cannot write " + file);
        }
    }
}
