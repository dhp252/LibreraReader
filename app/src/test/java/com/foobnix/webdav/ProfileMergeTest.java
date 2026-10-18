package com.foobnix.webdav;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileMergeTest {

    private static String progress(String... bookPageTime) throws Exception {
        final JSONObject o = new JSONObject();
        for (int i = 0; i < bookPageTime.length; i += 3) {
            o.put(bookPageTime[i], new JSONObject().put("p", Double.parseDouble(bookPageTime[i + 1]))
                    .put("t", Long.parseLong(bookPageTime[i + 2])));
        }
        return o.toString();
    }

    private static String list(String... pathTime) throws Exception {
        final JSONArray a = new JSONArray();
        for (int i = 0; i < pathTime.length; i += 2) {
            a.put(new JSONObject().put("path", pathTime[i]).put("name", "").put("time", Long.parseLong(pathTime[i + 1])));
        }
        return a.toString();
    }

    private static JSONObject obj(String text) throws Exception {
        return new JSONObject(text);
    }

    private static Set<String> paths(String listJson) throws Exception {
        final JSONArray a = new JSONArray(listJson);
        final Set<String> out = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            out.add(a.getJSONObject(i).getString("path"));
        }
        return out;
    }

    @Test
    public void handlesOnlyTheItemFiles() {
        assertTrue(ProfileMerge.handles("app-Progress.json"));
        assertTrue(ProfileMerge.handles("profile.Librera/app-Bookmarks.json"));
        assertTrue(ProfileMerge.handles("app-Tags2.json"));
        assertFalse(ProfileMerge.handles("ai/book.pdf.json"));
        assertFalse(ProfileMerge.handles("playlists/a.playlist"));
        assertFalse(ProfileMerge.handles("app-State.json"));
    }

    @Test
    public void positionsOfDifferentBooksFromTwoDevicesBothSurvive() throws Exception {
        final String base = progress("a.pdf", "0.1", "100", "b.pdf", "0.1", "100");
        final String local = progress("a.pdf", "0.5", "200", "b.pdf", "0.1", "100");
        final String remote = progress("a.pdf", "0.1", "100", "b.pdf", "0.7", "300");

        final JSONObject merged = obj(ProfileMerge.merge("app-Progress.json", base, local, remote));
        assertEquals(0.5, merged.getJSONObject("a.pdf").getDouble("p"), 0);
        assertEquals(0.7, merged.getJSONObject("b.pdf").getDouble("p"), 0);
    }

    @Test
    public void theSameBookReadOnBothDevicesKeepsTheMostRecentRead() throws Exception {
        final String base = progress("a.pdf", "0.5", "100");
        // the other device went back to re-read, later
        final String local = progress("a.pdf", "0.8", "200");
        final String remote = progress("a.pdf", "0.3", "300");

        final JSONObject merged = obj(ProfileMerge.merge("app-Progress.json", base, local, remote));
        assertEquals(0.3, merged.getJSONObject("a.pdf").getDouble("p"), 0);
    }

    @Test
    public void aBookmarkDeletedOnOneDeviceIsDeletedEverywhere() throws Exception {
        final String bookmark = new JSONObject().put("t", 100).put("text", "x").toString();
        final String base = "{\"100\":" + bookmark + ",\"200\":{\"t\":200}}";
        final String local = "{\"200\":{\"t\":200}}";
        final String remote = "{\"100\":" + bookmark + ",\"200\":{\"t\":200},\"300\":{\"t\":300}}";

        final JSONObject merged = obj(ProfileMerge.merge("app-Bookmarks.json", base, local, remote));
        assertFalse(merged.has("100"));
        assertTrue(merged.has("200"));
        assertTrue(merged.has("300"));
    }

    @Test
    public void anItemChangedOnOneSideAndDeletedOnTheOtherIsKept() throws Exception {
        final String base = progress("a.pdf", "0.1", "100");
        final String local = progress();
        final String remote = progress("a.pdf", "0.4", "200");

        final JSONObject merged = obj(ProfileMerge.merge("app-Progress.json", base, local, remote));
        assertEquals(0.4, merged.getJSONObject("a.pdf").getDouble("p"), 0);
    }

    @Test
    public void withoutAKeptCopyBothSidesAreJoined() throws Exception {
        final String local = list("/phone/Books/a.pdf", "100", "/phone/Books/b.pdf", "100");
        final String remote = list("/tablet/Books/b.pdf", "300", "/tablet/Books/c.pdf", "200");

        final String merged = ProfileMerge.merge("app-Recent.json", null, local, remote);
        // one entry per book name, the newer for b.pdf
        assertEquals(new HashSet<>(Arrays.asList("/phone/Books/a.pdf", "/tablet/Books/b.pdf", "/tablet/Books/c.pdf")),
                paths(merged));
    }

    @Test
    public void aFavouriteRemovedHereIsRemovedWhileOneAddedThereIsAdded() throws Exception {
        final String base = list("/b/a.pdf", "100", "/b/b.pdf", "100");
        final String local = list("/b/b.pdf", "100");
        final String remote = list("/b/a.pdf", "100", "/b/b.pdf", "100", "/b/c.pdf", "200");

        assertEquals(new HashSet<>(Arrays.asList("/b/b.pdf", "/b/c.pdf")),
                paths(ProfileMerge.merge("app-Favorite.json", base, local, remote)));
    }

    @Test
    public void tagsMergeTheirBooksAsSets() throws Exception {
        final String base = "{\"novel\":[\"a\",\"b\"],\"old\":[\"x\"]}";
        final String local = "{\"novel\":[\"a\",\"b\",\"c\"]}";          // added c, removed tag old
        final String remote = "{\"novel\":[\"b\"],\"old\":[\"x\"],\"new\":[\"z\"]}"; // removed a, added tag new

        final JSONObject merged = obj(ProfileMerge.merge("app-Tags2.json", base, local, remote));
        final JSONArray novel = merged.getJSONArray("novel");
        final Set<String> books = new HashSet<>();
        for (int i = 0; i < novel.length(); i++) {
            books.add(novel.getString(i));
        }
        assertEquals(new HashSet<>(Arrays.asList("b", "c")), books);
        assertFalse(merged.has("old"));
        assertTrue(merged.has("new"));
    }

    @Test
    public void aTagRemovedHereButGivenABookThereStays() throws Exception {
        final String base = "{\"novel\":[\"a\"]}";
        final String local = "{}";
        final String remote = "{\"novel\":[\"a\",\"b\"]}";

        final JSONObject merged = obj(ProfileMerge.merge("app-Tags2.json", base, local, remote));
        assertEquals(2, merged.getJSONArray("novel").length());
    }

    @Test
    public void keyOrderAndNumberFormatDoNotCountAsAChange() throws Exception {
        assertTrue(ProfileMerge.same(new JSONObject("{\"p\":1,\"t\":2}"), new JSONObject("{\"t\":2,\"p\":1.0}")));
        assertFalse(ProfileMerge.same(new JSONObject("{\"p\":1}"), new JSONObject("{\"p\":2}")));
    }

    @Test
    public void otherFilesAreNotMerged() throws Exception {
        assertNull(ProfileMerge.merge("ai/a.json", "{}", "{}", "{}"));
    }

    @Test
    public void theMarksOfABookAreMergedMarkByMark() throws Exception {
        assertTrue(ProfileMerge.handles("marks/Book.epub.json"));
        assertFalse(ProfileMerge.handles("ai/Book.epub.json"));
        String base = "{\"a\":{\"t\":1,\"k\":\"highlight\"},\"b\":{\"t\":1,\"k\":\"underline\"}}";
        // this device made c and erased b; the other made d and wrote a note on a
        String local = "{\"a\":{\"t\":1,\"k\":\"highlight\"},\"c\":{\"t\":2,\"k\":\"highlight\"}}";
        String remote = "{\"a\":{\"t\":3,\"k\":\"highlight\",\"note\":\"x\"},\"b\":{\"t\":1,\"k\":\"underline\"},"
                + "\"d\":{\"t\":2,\"k\":\"strike\"}}";
        JSONObject merged = obj(ProfileMerge.merge("marks/Book.epub.json", base, local, remote));
        assertEquals("x", merged.getJSONObject("a").getString("note"));
        assertFalse(merged.has("b"));
        assertTrue(merged.has("c"));
        assertTrue(merged.has("d"));
    }

    @Test
    public void aMarkChangedOnOneDeviceStaysWhenTheOtherErasedIt() throws Exception {
        String base = "{\"a\":{\"t\":1,\"col\":\"#ffd400\"}}";
        JSONObject merged = obj(ProfileMerge.merge("marks/Book.pdf.json", base, "{}", "{\"a\":{\"t\":5,\"col\":\"#ff6666\"}}"));
        assertEquals("#ff6666", merged.getJSONObject("a").getString("col"));
        // both changed it: the newer change
        merged = obj(ProfileMerge.merge("marks/Book.pdf.json", base, "{\"a\":{\"t\":7,\"col\":\"#5fb236\"}}",
                "{\"a\":{\"t\":5,\"col\":\"#ff6666\"}}"));
        assertEquals("#5fb236", merged.getJSONObject("a").getString("col"));
    }

    @Test
    public void studyAnswersOfTwoDevicesBothStay() throws Exception {
        assertTrue(ProfileMerge.handles("study/Book.epub.json"));
        String base = "{\"@book\":{\"t\":1,\"on\":true},\"c2\":{\"t\":1},\"c5\":{\"t\":1}}";
        // the phone answered chapter 2, the tablet chapter 5 and turned studying off
        String local = "{\"@book\":{\"t\":1,\"on\":true},\"c2\":{\"t\":4,\"a\":[\"phone\"]},\"c5\":{\"t\":1}}";
        String remote = "{\"@book\":{\"t\":3,\"on\":false},\"c2\":{\"t\":1},\"c5\":{\"t\":6,\"a\":[\"tablet\"]}}";
        JSONObject merged = obj(ProfileMerge.merge("study/Book.epub.json", base, local, remote));
        assertEquals("phone", merged.getJSONObject("c2").getJSONArray("a").getString(0));
        assertEquals("tablet", merged.getJSONObject("c5").getJSONArray("a").getString(0));
        assertFalse(merged.getJSONObject("@book").getBoolean("on"));
        // the same chapter on both: the newer
        merged = obj(ProfileMerge.merge("study/Book.epub.json", base, "{\"c2\":{\"t\":9,\"a\":[\"later\"]}}",
                "{\"c2\":{\"t\":4,\"a\":[\"earlier\"]}}"));
        assertEquals("later", merged.getJSONObject("c2").getJSONArray("a").getString(0));
    }
}
