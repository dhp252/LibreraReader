package com.foobnix.webdav;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Three-way merge of shared settings, one setting at a time (design:
 * .agents/planning/settings-sync-design.md, D6-D7). {@code base} is the copy this device merged
 * last time: a setting that differs from it was changed on that side. Values are strings.
 */
public final class SettingsMerge {

    public static final class Result {
        /** What every device should have now: uploaded, and kept as the next base. */
        public final Map<String, String> merged = new LinkedHashMap<>();
        /** Settings whose merged value came from the server: to be set on this device. */
        public final Set<String> fromRemote = new HashSet<>();
    }

    private SettingsMerge() {
    }

    /**
     * @param base       the last merged copy; null on this device's first merge, when the server
     *                   copy wins (D7)
     * @param remote     the server copy; null when there is none yet (this device's goes up)
     * @param localNewer this device's settings changed after the server copy was written: decides
     *                   a setting changed on both sides
     */
    public static Result merge(Map<String, String> base, Map<String, String> local, Map<String, String> remote,
                               boolean localNewer) {
        final Result result = new Result();
        final Set<String> keys = new TreeSet<>(local.keySet());
        if (remote != null) {
            keys.addAll(remote.keySet());
        }
        for (String key : keys) {
            final String l = local.get(key);
            String r = remote == null ? null : remote.get(key);
            final String value;
            if (remote == null) {
                value = l;
            } else if (l == null) {
                // a setting this build does not have: carried along for the devices that do
                value = r;
            } else if (base == null) {
                value = r != null ? r : l;
            } else {
                final String b = base.get(key);
                if (r == null) {
                    // missing on the server (an older build wrote it): unchanged there
                    r = b;
                }
                final boolean localChanged = !equal(l, b);
                final boolean remoteChanged = !equal(r, b);
                if (!remoteChanged) {
                    value = l;
                } else if (!localChanged) {
                    value = r;
                } else {
                    value = localNewer ? l : r;
                }
            }
            if (value == null) {
                continue;
            }
            result.merged.put(key, value);
            if (!equal(value, l)) {
                result.fromRemote.add(key);
            }
        }
        return result;
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
