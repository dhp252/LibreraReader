package com.foobnix.study;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StudyStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void keepsEverythingOfABookAndReadsItBackInOrder() throws Exception {
        final StudyStore store = new StudyStore(tmp.getRoot());
        store.update("Book.epub", b -> {
            b.on = true;
            b.thesis = "Pricing is a product decision";
            b.goal = "price my SaaS";
            b.previewed = 1000;
            b.split.add("part 1#0");
            final StudyBook.Chapter one = b.chapterOrNew("one#0");
            one.title = "One";
            one.label = StudyBook.SKIM;
            one.reason = "mostly cases";
            one.questions.addAll(Arrays.asList("Why?", "How?"));
            final StudyBook.Chapter two = b.chapterOrNew("two#0");
            two.title = "Two";
            two.answers.addAll(Arrays.asList("because", ""));
            two.feedback = new StudyBook.Feedback();
            two.feedback.items.add(new StudyBook.Item(0, StudyBook.Item.RIGHT, "yes", "the words"));
            final StudyBook.Node claim = new StudyBook.Node("A claim", "main", "a quote");
            claim.kids.add(new StudyBook.Node("Evidence", "evidence", ""));
            two.branch.add(claim);
            two.checked = 2000;
        });

        final StudyBook b = store.load("Book.epub");
        assertTrue(b.on);
        assertTrue(b.hasPreview());
        assertEquals("Pricing is a product decision", b.thesis);
        assertEquals("price my SaaS", b.goal);
        assertTrue(b.split.contains("part 1#0"));
        assertEquals(Arrays.asList("one#0", "two#0"), Arrays.asList(b.chapters.keySet().toArray()));
        assertEquals(StudyBook.SKIM, b.chapter("one#0").label);
        assertEquals(Arrays.asList("Why?", "How?"), b.chapter("one#0").questions);
        final StudyBook.Chapter two = b.chapter("two#0");
        assertTrue(two.hasAnswers());
        assertEquals("right", two.feedback.items.get(0).verdict);
        assertEquals("the words", two.feedback.items.get(0).quote);
        assertEquals(2, StudyBook.Node.count(two.branch));
        assertEquals("Evidence", two.branch.get(0).kids.get(0).text);
    }

    @Test
    public void aChangeNewTimesOnlyWhatItTouched() throws Exception {
        final StudyStore store = new StudyStore(tmp.getRoot());
        store.update("B.pdf", b -> {
            b.chapterOrNew("a#0").title = "A";
            b.chapterOrNew("b#0").title = "B";
        });
        final StudyBook first = store.load("B.pdf");
        final long a = first.chapter("a#0").t;
        final long b = first.chapter("b#0").t;
        final long book = first.t;
        Thread.sleep(5);

        store.update("B.pdf", x -> x.chapter("b#0").answers.add("my answer"));
        final StudyBook second = store.load("B.pdf");
        assertEquals(a, second.chapter("a#0").t);
        assertEquals(book, second.t);
        assertTrue(second.chapter("b#0").t > b);
    }

    @Test
    public void aTimeAlwaysGrowsEvenWithAClockBehind() {
        final StudyBook book = new StudyBook();
        final StudyBook.Chapter c = book.chapterOrNew("x#0");
        c.t = 5000;
        book.t = 5000;
        final java.util.Map<String, String> before = StudyStore.snapshot(book);
        c.recall = "something";
        StudyStore.stamp(book, before, 100);
        assertEquals(5001, c.t);
        assertEquals(5000, book.t);
    }

    @Test
    public void anUnreadableFileIsNeverOverwritten() throws Exception {
        final File folder = new File(tmp.getRoot(), StudyStore.FOLDER);
        assertTrue(folder.mkdirs());
        final File file = new File(folder, "B.pdf.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("{\"@book\": {\"on\": tr".getBytes(StandardCharsets.UTF_8));
        }
        final long length = file.length();
        final StudyStore store = new StudyStore(tmp.getRoot());
        assertFalse(store.load("B.pdf").on);
        try {
            store.update("B.pdf", b -> b.on = true);
            fail("must refuse");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(length, file.length());
    }

    @Test
    public void unknownLabelsReadAsRead() {
        assertEquals(StudyBook.READ, StudyBook.label("deep"));
        assertEquals(StudyBook.SKIP, StudyBook.label(" Skip "));
        assertEquals(StudyBook.READ, StudyBook.label(null));
    }

    @Test
    public void studyFilesAreKnownByTheirFolder() {
        assertTrue(StudyStore.isStudyFile("study/Book.epub.json"));
        assertTrue(StudyStore.isStudyFile("study\\Book.epub.json"));
        assertFalse(StudyStore.isStudyFile("marks/Book.epub.json"));
        assertEquals("study/Book.epub.json", StudyStore.profilePath("Book.epub"));
    }
}
