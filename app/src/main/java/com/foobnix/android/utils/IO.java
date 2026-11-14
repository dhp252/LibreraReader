package com.foobnix.android.utils;

import com.foobnix.mobi.parser.IOUtils;

import com.foobnix.LibreraApp;
import com.foobnix.pdf.info.AppsConfig;

import org.librera.JSONArray;
import org.librera.JSONException;
import org.librera.LinkedJSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class IO {

    static HashMap<Integer, Object> locks = new HashMap<>();

    public static synchronized Object getLock(File file) {
        Object l = locks.get(file.hashCode());
        if (l == null) {
            l = new Object();
            locks.put(file.hashCode(), l);
        } else {
            LOG.d("mylock", file.getPath(), l);
        }
        return l;
    }


    public static void writeObj(File file, Object o) {
        //new Thread(() -> writeObjAsync(file, o), "@T writeObj").start();
        AppsConfig.executorServiceSingle.execute(() -> writeObjSync(file, o));
    }

    public static void writeObjSync(File file, Object o) {
        LOG.d("writeObjAsync", file.getPath());
        if (o instanceof LinkedJSONObject || o instanceof JSONArray) {
            LOG.d("writeObjAsync", "LinkedJSONObject");
            IO.writeString(file, o.toString());
        } else if (o instanceof String) {
            LOG.d("writeObjAsync", "String");
            IO.writeString(file, (String) o);
        } else {
            //LOG.d("writeObjAsync", "Class", o.getClass().getName());
            IO.writeString(file, Objects.toJSONString(o));
        }
    }

    public static void readObj(File file, Object o) {

        try {
            if (!file.exists()) {
                LOG.d("readObj not exsits", file.getPath());
                return;
            }

            Objects.loadFromJson(o, readString(file));

        } catch (Exception e) {
            LOG.e(e);
        }

    }


    public static LinkedJSONObject readJsonObject(File file) {

        final String s = readString(file);
        if (TxtUtils.isEmpty(s)) {
            return new LinkedJSONObject();
        }

        try {
            return new LinkedJSONObject(s);
        } catch (JSONException e) {
            return new LinkedJSONObject();
        }

    }

    /**
     * The last file read or written, with its text. It is trusted only while the file on disk still
     * has the modified time and size it had then: the WebDAV sync replaces profile files behind
     * IO's back, and a stale copy written back would undo what the sync brought.
     */
    private static final class Cached {
        final String path;
        final String text;
        final long modified;
        final long length;

        Cached(File file, String text) {
            this.path = file.getPath();
            this.text = text;
            this.modified = file.lastModified();
            this.length = file.length();
        }

        boolean matches(File file) {
            return path.equals(file.getPath()) && file.lastModified() == modified && file.length() == length;
        }
    }

    private static volatile Cached cached;

    /** Forgets the cached file: call after something other than IO changed files on disk. */
    public static void invalidateCache() {
        cached = null;
    }

    public static String readString(File file) {
        return readString(file, false);
    }

    public static String readStringFromAsset(String assetName) throws IOException {
        InputStream open = LibreraApp.context.getAssets().open(assetName);
        return readString(open);
    }

    public static String readString(InputStream open) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(open, out);
        return out.toString().trim();
    }


    public static String readString(File file, boolean withSeparator) {
        final Cached c = cached;
        if (!withSeparator && c != null && c.matches(file)) {
            LOG.d("lib-IO", "read cache", file);
            return c.text;
        }
        synchronized (getLock(file)) {

            try {
                if (!file.exists()) {
                    return "";
                }
                LOG.d("lib-IO", "read file", file);
                StringBuilder builder = new StringBuilder();
                String aux = "";
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String separator = System.getProperty("line.separator");

                while ((aux = reader.readLine()) != null) {
                    builder.append(aux);
                    if (withSeparator) {
                        builder.append(separator);
                    }
                }
                reader.close();
                final String text = builder.toString();
                if (!withSeparator) {
                    cached = new Cached(file, text);
                }
                return text;
            } catch (Exception e) {
                LOG.e(e);
            }
            cached = null;
            return "";
        }
    }

    public static boolean writeString(File file, String string) {

        synchronized (getLock(file)) {

            try {
                if (string == null) {
                    string = "";
                }
                LOG.d("lib-IO", "write file", file);
                new File(file.getParent()).mkdirs();

                // written beside and renamed over the file in one step: the WebDAV sync reading the
                // profile files never finds one empty or half written (and takes it for "all deleted")
                final File temp = new File(file.getPath() + ".io.tmp");
                OutputStream out = new BufferedOutputStream(new FileOutputStream(temp));
                try {
                    out.write(string.getBytes());
                    out.flush();
                } finally {
                    out.close();
                }
                if (!temp.renameTo(file)) {
                    // a system that cannot rename over a file
                    if (file.exists() && !file.delete()) {
                        temp.delete();
                        throw new IOException("cannot replace " + file);
                    }
                    if (!temp.renameTo(file)) {
                        temp.delete();
                        throw new IOException("cannot write " + file);
                    }
                }

                cached = new Cached(file, string);

            } catch (Exception e) {
                LOG.e(e);
                cached = null;
                return false;
            }
            return true;
        }
    }

    public static boolean copyFile(File from, File to) {
        try {
            new File(to.getParent()).mkdirs();

            InputStream input = new BufferedInputStream(new FileInputStream(from));
            OutputStream output = new BufferedOutputStream(new FileOutputStream(to));

            IOUtils.copyClose(input, output);

            LOG.d("Copy file form to", from, to);
        } catch (IOException e) {
            LOG.e(e);
            return false;
        }
        return true;
    }

    public static boolean copyFile(InputStream from, File to) {
        try {
            new File(to.getParent()).mkdirs();

            InputStream input = new BufferedInputStream(from);
            OutputStream output = new BufferedOutputStream(new FileOutputStream(to));

            IOUtils.copyClose(input, output);

            LOG.d("Copy file form to", from, to);
        } catch (IOException e) {
            LOG.e(e);
            return false;
        }
        return true;
    }
}
