package com.foobnix.study;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OutlineTest {

    /** Ch 1 read and checked (a claim with evidence), ch 2 skipped, ch 3 read but not checked. */
    private static StudyBook book() {
        final StudyBook b = new StudyBook();
        b.thesis = "Price is a product decision";
        final StudyBook.Chapter one = b.chapterOrNew("one#0");
        one.title = "Why <prices> matter";
        one.from = 11;
        final StudyBook.Node claim = new StudyBook.Node("Value sets the price", "main", "Value sets the price.");
        claim.page = 13;
        claim.kids.add(new StudyBook.Node("A survey of 300 firms", "evidence", ""));
        one.branch.add(claim);
        final StudyBook.Chapter two = b.chapterOrNew("two#0");
        two.title = "History";
        two.label = StudyBook.SKIP;
        two.line = "How prices were set before";
        two.from = 20;
        final StudyBook.Chapter three = b.chapterOrNew("three#0");
        three.title = "Tests";
        three.line = "Three ways to test a price";
        three.from = 30;
        return b;
    }

    private static Map<String, List<StudyBook.Node>> notes() {
        final StudyBook.Node note = new StudyBook.Node("try this on my plan", Outline.NOTE, "the decoy");
        note.page = 33;
        final Map<String, List<StudyBook.Node>> m = new HashMap<>();
        m.put("three#0", Collections.singletonList(note));
        return m;
    }

    @Test
    public void theOutlineFollowsTheChaptersAndTheirBranches() {
        final Outline.Item root = Outline.build("Pricing", book(), notes());
        assertEquals("Price is a product decision", root.detail);
        assertEquals(3, root.kids.size());
        final Outline.Item one = root.kids.get(0);
        assertEquals("1. Why <prices> matter", one.text);
        assertEquals("Value sets the price", one.kids.get(0).text);
        assertEquals("evidence", one.kids.get(0).kids.get(0).category);
        assertEquals("c1.1.1", one.kids.get(0).kids.get(0).id);
        assertTrue("a skipped chapter is faint", root.kids.get(1).dim);
        assertEquals(Outline.LINE, root.kids.get(1).kids.get(0).category);
        assertFalse(root.kids.get(2).dim);
        assertEquals(Outline.NOTE, root.kids.get(2).kids.get(1).category);
        assertEquals("three#0", root.kids.get(2).kids.get(1).chapter);
        assertEquals(9, root.count());
        assertNotNull(Outline.find(root, "c3.2"));
        assertNull(Outline.find(root, "c9"));
    }

    @Test
    public void theMarkdownIsANestedList() {
        final String md = Outline.toMarkdown(Outline.build("Pricing", book(), notes()));
        assertTrue(md.startsWith("**Thesis:** Price is a product decision\n\n"));
        assertTrue(md.contains("- **1. Why <prices> matter** · p. 12\n"));
        assertTrue(md.contains("  - 🟥 Main idea: Value sets the price · p. 14\n"));
        assertTrue(md.contains("    - 🟦 Evidence: A survey of 300 firms\n"));
        assertTrue(md.contains("- **2. History** · p. 21 · skipped\n  - *How prices were set before*\n"));
        assertTrue(md.contains("  - 🟨 Note: try this on my plan · p. 34\n"));
    }

    @Test
    public void theMindMapEscapesTextAndCarriesIdsAndColors() throws Exception {
        final JSONObject map = Outline.toMarkmap(Outline.build("Pricing & co", book(), null));
        assertEquals("<b>Pricing &amp; co</b><br><i>Price is a product decision</i>", map.getString("content"));
        final JSONObject one = map.getJSONArray("children").getJSONObject(0);
        assertEquals("<b>1. Why &lt;prices&gt; matter</b>", one.getString("content"));
        assertEquals("c1", one.getJSONObject("payload").getString("id"));
        assertEquals("#5fb236", one.getJSONObject("payload").getString("color"));
        final JSONObject claim = one.getJSONArray("children").getJSONObject(0);
        assertEquals("#ff6666", claim.getJSONObject("payload").getString("color"));
        final JSONObject two = map.getJSONArray("children").getJSONObject(1);
        assertTrue(two.getString("content").startsWith("<span style=\"opacity:.5\">"));
    }

    @Test
    public void anOutlineHasBranchesOnceAChapterWasChecked() {
        final StudyBook b = new StudyBook();
        b.chapterOrNew("a").line = "x";
        assertFalse(Outline.hasBranches(b));
        b.chapter("a").branch.addAll(Arrays.asList(new StudyBook.Node("claim", "main", "")));
        assertTrue(Outline.hasBranches(b));
    }
}
