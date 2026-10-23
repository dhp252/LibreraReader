package com.foobnix.webdav;

import com.foobnix.dao2.FileMeta;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The library metadata of a server book (title, author, series, pages...), shared like its cover:
 * {@code Books/.covers/<book file name>.json}, written by the device that read the book (one that has
 * it, or the one that fetched it for its cover), so every device shows server-only books with the
 * same details as downloaded ones.
 */
public final class WebDavBookMeta {

    static final String JSON = ".json";

    private static final String[] TEXTS = {"title", "author", "sequence", "genre", "annotation", "lang",
            "keyword", "pubDate", "publisher", "isbn"};

    private WebDavBookMeta() {
    }

    /** "Books/a/b.pdf" -> "Books/.covers/b.pdf.json"; null for a path outside Books. */
    public static String metaOf(String bookPath) {
        final String cover = WebDavCovers.coverOf(bookPath);
        return cover == null ? null : cover.substring(0, cover.length() - WebDavCovers.JPG.length()) + JSON;
    }

    /** The metadata file name of a book file name: "b.pdf" -> "b.pdf.json". */
    public static String metaNameOf(String bookName) {
        return bookName + JSON;
    }

    public static String toJson(FileMeta meta) throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("title", meta.getTitle());
        o.put("author", meta.getAuthor());
        o.put("sequence", meta.getSequence());
        o.put("genre", meta.getGenre());
        o.put("annotation", meta.getAnnotation());
        o.put("lang", meta.getLang());
        o.put("keyword", meta.getKeyword());
        o.put("pubDate", meta.getPubDate());
        o.put("publisher", meta.getPublisher());
        o.put("isbn", meta.getIsbn());
        if (meta.getSIndex() != null) {
            o.put("sIndex", meta.getSIndex());
        }
        if (meta.getPages() != null) {
            o.put("pages", meta.getPages());
        }
        if (meta.getYear() != null) {
            o.put("year", meta.getYear());
        }
        return o.toString();
    }

    /** Sets what the JSON holds on an item; a damaged file leaves the item as it was. */
    public static void apply(String json, FileMeta item) {
        final JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            return;
        }
        for (String name : TEXTS) {
            final String value = o.isNull(name) ? null : o.optString(name, null);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            switch (name) {
                case "title":
                    item.setTitle(value);
                    break;
                case "author":
                    item.setAuthor(value);
                    break;
                case "sequence":
                    item.setSequence(value);
                    break;
                case "genre":
                    item.setGenre(value);
                    break;
                case "annotation":
                    item.setAnnotation(value);
                    break;
                case "lang":
                    item.setLang(value);
                    break;
                case "keyword":
                    item.setKeyword(value);
                    break;
                case "pubDate":
                    item.setPubDate(value);
                    break;
                case "publisher":
                    item.setPublisher(value);
                    break;
                case "isbn":
                    item.setIsbn(value);
                    break;
                default:
                    break;
            }
        }
        if (o.has("sIndex")) {
            item.setSIndex(o.optInt("sIndex"));
        }
        if (o.optInt("pages", 0) > 0) {
            item.setPages(o.optInt("pages"));
        }
        if (o.optInt("year", 0) > 0) {
            item.setYear(o.optInt("year"));
        }
    }
}
