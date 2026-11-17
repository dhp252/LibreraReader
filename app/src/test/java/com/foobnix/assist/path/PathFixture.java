package com.foobnix.assist.path;

import com.foobnix.ai.BookChapters;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.assist.skeleton.SkeletonParts;

import java.util.Arrays;
import java.util.List;

/** A small book for the path tests: an introduction, two books with numbered chapters, a conclusion. */
final class PathFixture {

    private PathFixture() {
    }

    static final int PAGES = 80;

    static final String INTRO = "introduction#0";
    static final String LABOUR = "book i#0/chapter i: of the division of labour#0";
    static final String PRINCIPLE = "book i#0/chapter ii: of the principle#0";
    static final String PRICE = "book i#0/chapter vii: of the natural and market price#0";
    static final String STOCK = "book ii#0/chapter i: of stock#0";
    static final String HAND = "book ii#0/chapter ii: the invisible hand#0";
    static final String CONCLUSION = "conclusion#0";

    private static BookChapters.Entry e(String title, int level, int page) {
        return new BookChapters.Entry(title, level, page);
    }

    static List<SkeletonParts.Part> parts() {
        return SkeletonParts.group(Arrays.asList(
                e("Contents", 0, 2),
                e("Introduction", 0, 4),
                e("Book I", 0, 10),
                e("Chapter I: Of the Division of Labour", 1, 11),
                e("Chapter II: Of the Principle", 1, 20),
                e("Chapter VII: Of the Natural and Market Price", 1, 30),
                e("Book II", 0, 40),
                e("Chapter I: Of Stock", 1, 40),
                e("Chapter II: The Invisible Hand", 1, 50),
                e("Conclusion", 0, 60),
                e("Index", 0, 70)), PAGES);
    }

    /** A skeleton with a line for every chapter; 6000 characters (1000 words) each, the goal chapter twice that. */
    static SkeletonData skeleton() {
        final SkeletonData d = new SkeletonData();
        line(d, INTRO, "What the book sets out to show", 6000);
        line(d, LABOUR, "Splitting work multiplies output", 6000);
        line(d, PRINCIPLE, "Self-interest drives exchange", 6000);
        line(d, PRICE, "Prices pull supply towards demand", 6000);
        line(d, STOCK, "Savings become capital", 6000);
        line(d, HAND, "Owners serve the public without meaning to", 12000);
        line(d, CONCLUSION, "What follows for governments", 6000);
        return d;
    }

    private static void line(SkeletonData d, String key, String line, long chars) {
        d.chapterLines.put(key, line);
        d.chars.put(key, chars);
    }

    static List<PathChapters.Entry> list() {
        return PathChapters.list(parts(), skeleton());
    }

    /** The path Labour, Principle, Price, Hand (the goal), made at 230 words a minute's default speed. */
    static PathData path() {
        final PathPrompts.Answer a = new PathPrompts.Answer();
        a.steps.add(new PathPrompts.Step(1, "", "Why people specialize at all"));
        a.steps.add(new PathPrompts.Step(2, "", "Why no one needs to be in charge"));
        a.steps.add(new PathPrompts.Step(3, "", "How prices steer effort"));
        a.steps.add(new PathPrompts.Step(5, "", "The passage you asked for"));
        a.goal = 5;
        a.skipped = "stock, governments";
        a.read = true;
        return PathData.fromAnswer("the invisible hand", a, list(), "model", 7L);
    }
}
