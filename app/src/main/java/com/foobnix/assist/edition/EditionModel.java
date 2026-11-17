package com.foobnix.assist.edition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A book's edition as it is read: up to five sections (the skeleton, in your words, what you got
 * wrong and the fix, connections, questions still open), each a list of chapters with their
 * blocks. Built by {@link EditionBuilder} from what the reader produced in the book and nothing
 * else; a section without anything in it is not there, and neither is a chapter. Cut into pages
 * by {@link EditionPages}, into printed lines by {@link EditionPrint}.
 */
public final class EditionModel {

    /** The five sections, in the order of the contents. */
    public enum Part {
        SKELETON, WORDS, WRONG, CONNECTIONS, OPEN
    }

    /** What a block is, which decides how it is set. */
    public enum Kind {
        /** The book's one line. */
        LINE,
        /** The book's argument in a paragraph (only when the table of contents cannot be read). */
        PARAGRAPH,
        /** A part of the book with its line. */
        PART,
        /** A chapter with its line. */
        CHAPTER_LINE,
        /** The reader's own recall. */
        WORDS,
        /** The ideas the reader explained in a teach-back. */
        EXPLAINED,
        /** What the reader missed, with the idea and the book's words. */
        WRONG,
        /** A finished experiment. */
        TRIED,
        /** A connection to another book the reader added. */
        LINK,
        /** A note of "The book, updated" the reader chose. */
        NOTE,
        /** Where the reader and the author really disagree. */
        OPEN,
        /** A pre-question the reader guessed wrong. */
        GUESS
    }

    /** One labelled piece of a chapter. */
    public static final class Block {
        public final Kind kind;
        /** The small label over it ("In your words"); may be empty. */
        public final String label;
        /** A title line (a part's or chapter's title, a connection, a note's title); may be empty. */
        public final String title;
        /** The text; paragraphs are separated by a blank line. */
        public final String text;
        /** A small line under it ("From your debate on: ..."); may be empty. */
        public final String caption;

        public Block(Kind kind, String label, String title, String text, String caption) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
            this.caption = caption == null ? "" : caption;
        }

        /** The same block with another text (a piece of it on a page). */
        public Block with(String newText, boolean first) {
            return new Block(kind, label, first ? title : "", newText, "");
        }

        /** Characters of everything it shows. */
        public int length() {
            return label.length() + title.length() + text.length() + caption.length();
        }
    }

    /** A chapter inside a section. */
    public static final class Group {
        /** The chapter key; empty for the skeleton and for material without a chapter. */
        public final String key;
        /** The chapter's title as the book has it; empty for no heading. */
        public final String title;
        public final List<Block> blocks;

        public Group(String key, String title, List<Block> blocks) {
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        }
    }

    public static final class Section {
        public final Part part;
        public final String title;
        public final List<Group> groups;

        public Section(Part part, String title, List<Group> groups) {
            this.part = part;
            this.title = title;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        }
    }

    /** A chapter's recall as the "Tidy my words" request needs it. */
    public static final class Raw {
        public final String key;
        public final String title;
        public final String words;
        public final List<String> ideas;

        public Raw(String key, String title, String words, List<String> ideas) {
            this.key = key;
            this.title = title;
            this.words = words;
            this.ideas = ideas;
        }
    }

    public String bookTitle = "";
    public final List<Section> sections = new ArrayList<>();
    /** Chapters with anything the reader produced. */
    public int chapters;
    /** What it is made of, as the cover names it: recalls, misses, experiments, links, questions. */
    public final List<String> kinds = new ArrayList<>();
    /** The cover's sample: a chapter's title, the start of the reader's words on it, and a note; empty without a recall. */
    public String sampleChapter = "";
    public String sampleText = "";
    public String sampleNote = "";
    /** The bold start of {@link #sampleNote} ("Missed "switching" at first"); empty when there is none. */
    public String sampleNoteLead = "";
    /** Every recall with words, raw: what "Tidy my words" would send. */
    public final List<Raw> raws = new ArrayList<>();
    /** Titles of the chapters that have a tidied passage for their current words. */
    public final List<String> tidied = new ArrayList<>();
    /** The reader went back to their raw words. */
    public boolean showsRaw;

    /** Nothing the reader produced: the screen says what feeds an edition instead of showing pages. */
    public boolean isEmpty() {
        return chapters == 0;
    }

    public Section section(Part part) {
        for (Section s : sections) {
            if (s.part == part) {
                return s;
            }
        }
        return null;
    }

    /** Words in everything shown. */
    public int wordCount() {
        int n = 0;
        for (Section s : sections) {
            for (Group g : s.groups) {
                n += words(g.title);
                for (Block b : g.blocks) {
                    n += words(b.title) + words(b.text) + words(b.caption);
                }
            }
        }
        return n;
    }

    /** Minutes to read it at an easy pace, at least one. */
    public int minutes() {
        return Math.max(1, Math.round(wordCount() / 200f));
    }

    static int words(String s) {
        if (s == null) {
            return 0;
        }
        final String t = s.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }
}
