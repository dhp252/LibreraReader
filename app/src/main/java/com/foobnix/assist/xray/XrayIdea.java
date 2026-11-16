package com.foobnix.assist.xray;

import com.foobnix.ai.AiCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One idea of a chapter's key passages as the X-ray reads it (a copy of {@code AiStore.Idea} with
 * its marks), so the map and the rating prompt stay plain Java.
 */
public final class XrayIdea {
    public final int id;
    public final String text;
    public final AiCategory category;
    /** The idea it supports, or -1. */
    public final int parent;
    /** A mark of it was found in the book. */
    public final boolean marked;
    /** 0-based page of its first mark, or -1. */
    public final int page;
    /** Its marks' words (key sentences first, then must-read paragraphs), in the chapter's order. */
    public final List<String> quotes;
    /** The words of the mark a tap goes to (the first one in the book), or null. */
    public final String first;
    /** 0-based page {@link #first} was found on, or -1. */
    public final int firstPage;

    public XrayIdea(int id, String text, AiCategory category, int parent, boolean marked, int page,
                    List<String> quotes, String first, int firstPage) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.category = category;
        this.parent = parent;
        this.marked = marked;
        this.page = page;
        this.quotes = quotes == null ? Collections.<String>emptyList() : new ArrayList<>(quotes);
        this.first = first;
        this.firstPage = firstPage;
    }

    public XrayKind kind() {
        return XrayKind.of(category);
    }
}
