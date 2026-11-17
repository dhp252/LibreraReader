package com.foobnix.assist.lens;

/** One stored key-passage idea of a chapter as the lens sends it: its text and the mark a tap goes to. */
public final class LensIdea {
    /** Unique in its chapter (the key passages' idea id). */
    public final int id;
    public final String text;
    /** main, evidence, example, caveat, concept or apply; may be empty. */
    public final String kind;
    /** The book's words of its first mark, or null when it has none. */
    public final String quote;
    /** 0-based page of that mark when it was picked (a hint), or -1. */
    public final int page;

    public LensIdea(int id, String text, String kind, String quote, int page) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.kind = kind == null ? "" : kind;
        this.quote = quote;
        this.page = page;
    }
}
