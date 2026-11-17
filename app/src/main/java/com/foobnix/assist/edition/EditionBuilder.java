package com.foobnix.assist.edition;

import com.foobnix.assist.debate.DebateResult;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.assist.teach.Teach;
import com.foobnix.assist.useit.UseDates;
import com.foobnix.assist.useit.UseExperiment;
import com.foobnix.assist.edition.EditionModel.Block;
import com.foobnix.assist.edition.EditionModel.Kind;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Assembles a book's edition from the items of its assist file (and the library's connections):
 * only what the reader produced, read as the features that wrote it store it, and tolerantly, so
 * a missing or half-written item leaves its piece out and nothing else. No AI request and no
 * Android: the same items always give the same edition.
 * <ul>
 * <li>{@code skeleton:book}: the book's one line with its parts' and chapters' lines;</li>
 * <li>{@code recall:<chapter>}: the reader's words, and the ideas missed or half remembered with
 * the book's own words for them; {@code teach:<chapter>}: ideas explained, gaps;</li>
 * <li>{@code use:<id>} once done: "Tried it";</li>
 * <li>{@code review:<chapter>:<idea>}: how the misses went since ("right on every review since");</li>
 * <li>{@code link:<id>} of the library, state added: connections; {@code edition:note:...}: notes
 * of "The book, updated" the reader chose;</li>
 * <li>{@code debate:<chapter>:<idea>}: where the reader really disagrees; {@code preq:<chapter>}:
 * guesses that were wrong.</li>
 * </ul>
 */
public final class EditionBuilder {

    static final String REVIEW = "review:";
    static final String LINK = "link:";
    static final String PREQ = "preq:";
    /** Characters of the reader's words on the cover's sample. */
    static final int SAMPLE_CHARS = 220;
    /** Chapter lines shown under the parts' lines only in a book with this many chapters at most. */
    static final int FEW_CHAPTERS = 12;

    private EditionBuilder() {
    }

    /** What a chapter gathers before it is laid into the sections. */
    private static final class Chapter {
        final String key;
        String title = "";
        final List<Block> words = new ArrayList<>();
        final List<String> wrong = new ArrayList<>();
        /** The misses already listed, normalized, so the same idea is not listed twice. */
        final List<String> listed = new ArrayList<>();
        final List<Block> links = new ArrayList<>();
        final List<Block> open = new ArrayList<>();
        String missLead = "";
        boolean missRight;

        Chapter(String key) {
            this.key = key;
        }

        boolean any() {
            return !words.isEmpty() || !wrong.isEmpty() || !links.isEmpty() || !open.isEmpty();
        }
    }

    /** One review of an idea. */
    private static final class Shown {
        final String day;
        final boolean right;

        Shown(String day, boolean right) {
            this.day = day;
            this.right = right;
        }
    }

    /** An idea in the spaced review. */
    private static final class Review {
        int id = -1;
        String idea = "";
        String title = "";
        String reason = "";
        final List<Shown> history = new ArrayList<>();
        boolean used;
    }

    /**
     * @param bookName  the book's name in the assist store
     * @param bookTitle its title as shown
     * @param book      every item of the book's assist file, by key
     * @param library   the items of the library file (only {@code link:} ones are read); may be null
     * @param outline   the book's parts and chapters in order; empty when not known
     * @param today     the local day, for how days are written
     */
    public static EditionModel build(String bookName, String bookTitle, Map<String, JSONObject> book,
                                     Map<String, JSONObject> library, EditionOutline outline, EditionWords w,
                                     String today, TimeZone zone) {
        final EditionModel m = new EditionModel();
        m.bookTitle = bookTitle == null ? "" : bookTitle;
        if (book == null) {
            book = Collections.emptyMap();
        }
        if (outline == null) {
            outline = new EditionOutline();
        }
        final EditionState state = EditionState.fromJson(book.get(EditionState.KEY));
        m.showsRaw = state.raw;
        final Map<String, Chapter> chapters = new LinkedHashMap<>();
        final Map<String, List<Review>> reviews = reviews(book, zone);

        boolean recalls = false;
        boolean experiments = false;
        boolean links = false;

        // the reader's own words, and what the check found missing
        for (Map.Entry<String, JSONObject> e : sorted(book, Recall.PREFIX)) {
            final Recall.Attempt a = attempt(e.getValue());
            if (a == null || !a.isDone()) {
                continue;
            }
            final Chapter c = chapter(chapters, e.getKey().substring(Recall.PREFIX.length()), a.title);
            final String raw = a.words.trim();
            if (!raw.isEmpty()) {
                final List<String> ideas = new ArrayList<>();
                for (Recall.Idea i : a.ideas) {
                    ideas.add(i.text);
                }
                m.raws.add(new EditionModel.Raw(c.key, "", raw, ideas));
                final String tidy = EditionPrompts.tidied(book.get(EditionPrompts.key(c.key)), raw);
                if (tidy != null) {
                    m.tidied.add(c.key);
                }
                final boolean shown = tidy != null && !state.raw;
                c.words.add(new Block(Kind.WORDS, shown ? w.labelTidied : w.labelWords, "", shown ? tidy : raw, ""));
                recalls = true;
            }
            final List<Review> known = reviews.get(c.key);
            for (Recall.Idea i : a.ideas) {
                final boolean missed = Recall.MISSED.equals(i.state);
                if (!missed && !Recall.UNSURE.equals(i.state)) {
                    continue;
                }
                final Review r = review(known, i.id, i.text);
                // the book's words only from the chapter's key passages (marked in the book), never the check's own
                miss(c, w, missed, i.text, a.fromPassages ? i.quote() : "", r, today);
            }
        }
        // ideas the review brought back that the latest recall no longer marks
        for (Map.Entry<String, List<Review>> e : reviews.entrySet()) {
            for (Review r : e.getValue()) {
                final int kind = missKind(r.reason);
                if (r.used || kind == 0 || r.idea.isEmpty()) {
                    continue;
                }
                final Chapter c = chapter(chapters, e.getKey(), r.title);
                if (!c.listed.contains(norm(r.idea))) {
                    miss(c, w, kind == 2, r.idea, "", r, today);
                }
            }
        }
        // teach-back: what the reader could explain, and the gaps the book's words settled
        for (Map.Entry<String, JSONObject> e : sorted(book, Teach.PREFIX)) {
            final JSONObject o = e.getValue();
            final List<String> explained = strings(o.optJSONArray("explained"));
            final JSONArray gaps = o.optJSONArray("gaps");
            final List<String> missing = o.optBoolean("finished") ? strings(o.optJSONArray("missing"))
                    : new ArrayList<String>();
            if (explained.isEmpty() && (gaps == null || gaps.length() == 0) && missing.isEmpty()) {
                continue;
            }
            final Chapter c = chapter(chapters, e.getKey().substring(Teach.PREFIX.length()), o.optString("title", ""));
            if (!explained.isEmpty()) {
                final StringBuilder list = new StringBuilder();
                for (String s : explained) {
                    list.append(list.length() > 0 ? "\n" : "").append("• ").append(s);
                }
                c.words.add(new Block(Kind.EXPLAINED, w.labelExplained, "", list.toString(), ""));
                recalls = true;
            }
            for (int i = 0; gaps != null && i < gaps.length(); i++) {
                final JSONObject g = gaps.optJSONObject(i);
                final String say = g == null ? "" : one(g.optString("say", ""));
                if (say.isEmpty()) {
                    continue;
                }
                final String quote = one(g.optString("quote", ""));
                c.wrong.add(sentence(say) + (quote.isEmpty() ? "" : " " + EditionWords.f(w.bookSays, quote)));
                c.listed.add(norm(g.optString("idea", "")));
            }
            for (String s : missing) {
                if (!c.listed.contains(norm(s))) {
                    c.wrong.add(EditionWords.f(w.leftOut, sentence(s)));
                    c.listed.add(norm(s));
                }
            }
        }
        // finished experiments
        for (Map.Entry<String, JSONObject> e : sorted(book, UseExperiment.PREFIX)) {
            final UseExperiment x = experiment(e.getValue());
            if (x == null || !UseExperiment.DONE.equals(x.state)) {
                continue;
            }
            final Chapter c = chapter(chapters, x.chapterKey, x.chapterTitle);
            final String dates = EditionDates.range(x.startDay, x.doneDay.isEmpty() ? x.checkDay : x.doneDay, today, w.months);
            c.words.add(new Block(Kind.TRIED, dates.isEmpty() ? w.labelTried : EditionWords.f(w.labelTriedDates, dates),
                    "", tried(x, w), ""));
            experiments = true;
        }
        // connections the reader added, and the notes of "The book, updated" they chose
        if (library != null) {
            for (Map.Entry<String, JSONObject> e : sorted(library, LINK)) {
                final JSONObject o = e.getValue();
                if (!"added".equalsIgnoreCase(o.optString("state", "").trim())) {
                    continue;
                }
                final JSONObject a = o.optJSONObject("a");
                final JSONObject b = o.optJSONObject("b");
                final boolean aMine = isBook(a, bookName);
                final JSONObject mine = aMine ? a : isBook(b, bookName) ? b : null;
                final JSONObject other = aMine ? b : a;
                if (mine == null || other == null) {
                    continue;
                }
                final String idea = one(any(other, "idea", "text"));
                if (idea.isEmpty()) {
                    continue;
                }
                final String relation = relation(o.optString("relation", ""), w);
                final String otherBook = isBook(other, bookName) ? "" : one(any(other, "title", "bookTitle", "book_title"));
                final String title = join(" · ", relation, idea, otherBook);
                final String text = one(any(o, "sentence", "why"));
                final Chapter c = chapter(chapters, any(mine, "chapter", "chapterKey", "chapter_key", "key"),
                        any(mine, "chapterTitle", "chapter_title"));
                c.links.add(new Block(Kind.LINK, w.labelConnections, title, text, ""));
                links = true;
            }
        }
        for (Map.Entry<String, JSONObject> e : sorted(book, EditionNote.PREFIX)) {
            final EditionNote n = EditionNote.fromJson(e.getValue());
            final String key = EditionNote.chapterOf(e.getKey());
            if (n == null || key == null) {
                continue;
            }
            final Chapter c = chapter(chapters, key, n.chapterTitle);
            final String settled = settled(n.settled, w);
            c.links.add(new Block(Kind.NOTE, w.labelNote, n.title, n.body, settled.isEmpty() ? ""
                    : EditionWords.f(w.howSettled, n.settledNote.isEmpty() ? settled : settled + ", " + n.settledNote)));
            links = true;
        }
        // where the reader really disagrees, and guesses that were wrong
        for (Map.Entry<String, JSONObject> e : sorted(book, DebateResult.PREFIX)) {
            final String crux = one(e.getValue().optString("crux", ""));
            final String rest = e.getKey().substring(DebateResult.PREFIX.length());
            final int at = rest.lastIndexOf(':');
            if (crux.isEmpty() || at < 0) {
                continue;
            }
            final String claim = one(e.getValue().optString("claim", ""));
            chapter(chapters, rest.substring(0, at), "").open.add(new Block(Kind.OPEN, w.labelOpen, "", crux,
                    claim.isEmpty() ? "" : EditionWords.f(w.debateOn, claim)));
        }
        for (Map.Entry<String, JSONObject> e : sorted(book, PREQ)) {
            final JSONArray questions = e.getValue().optJSONArray("questions");
            for (int i = 0; questions != null && i < questions.length(); i++) {
                final JSONObject q = questions.optJSONObject(i);
                final JSONArray options = q == null ? null : q.optJSONArray("options");
                if (options == null || q.optBoolean("skipped")) {
                    continue;
                }
                final int guess = q.optInt("guess", -1);
                final int answer = q.optInt("answer", -1);
                final String text = one(q.optString("q", ""));
                if (guess < 0 || guess == answer || guess >= options.length() || answer < 0 || answer >= options.length()
                        || text.isEmpty()) {
                    continue;
                }
                final String guessed = one(options.optString(guess, ""));
                final String right = one(options.optString(answer, ""));
                if (guessed.isEmpty() || right.isEmpty()) {
                    continue;
                }
                chapter(chapters, e.getKey().substring(PREQ.length()), e.getValue().optString("title", "")).open
                        .add(new Block(Kind.GUESS, w.labelGuess, "", text, EditionWords.f(w.guess, guessed, right)));
            }
        }

        // the chapters in the book's order, named as the book names them
        final List<Chapter> order = new ArrayList<>();
        for (Chapter c : chapters.values()) {
            if (c.any()) {
                final String title = outline.title(c.key);
                c.title = !title.isEmpty() ? title : !c.title.isEmpty() ? c.title : titleOf(c.key, w);
                order.add(c);
            }
        }
        final EditionOutline o = outline;
        Collections.sort(order, (a, b) -> {
            final int x = o.order(a.key);
            final int y = o.order(b.key);
            if (x >= 0 && y >= 0) {
                return Integer.compare(x, y);
            }
            if (x >= 0 || y >= 0) {
                return x >= 0 ? -1 : 1;
            }
            final int byTitle = a.title.compareToIgnoreCase(b.title);
            return byTitle != 0 ? byTitle : a.key.compareTo(b.key);
        });
        m.chapters = order.size();

        final List<EditionModel.Group> words = new ArrayList<>();
        final List<EditionModel.Group> wrong = new ArrayList<>();
        final List<EditionModel.Group> connections = new ArrayList<>();
        final List<EditionModel.Group> open = new ArrayList<>();
        boolean misses = false;
        boolean questions = false;
        for (Chapter c : order) {
            if (!c.words.isEmpty()) {
                words.add(new EditionModel.Group(c.key, c.title, c.words));
            }
            if (!c.wrong.isEmpty()) {
                wrong.add(new EditionModel.Group(c.key, c.title, Collections.singletonList(
                        new Block(Kind.WRONG, w.labelWrong, "", join("\n\n", c.wrong.toArray(new String[0])), ""))));
                misses = true;
            }
            if (!c.links.isEmpty()) {
                connections.add(new EditionModel.Group(c.key, c.title, c.links));
            }
            if (!c.open.isEmpty()) {
                open.add(new EditionModel.Group(c.key, c.title, c.open));
                questions = true;
            }
            if (m.sampleText.isEmpty()) {
                for (Block b : c.words) {
                    if (b.kind == Kind.WORDS) {
                        m.sampleChapter = c.title;
                        m.sampleText = cut(b.text, SAMPLE_CHARS);
                        m.sampleNoteLead = c.missLead;
                        m.sampleNote = c.missLead.isEmpty() ? "" : c.missRight ? c.missLead + " · " + w.rightSinceShort
                                : c.missLead;
                        break;
                    }
                }
            }
        }
        for (EditionModel.Raw r : new ArrayList<>(m.raws)) {
            // the request and the cover name a chapter by its title
            final Chapter c = chapters.get(r.key);
            m.raws.set(m.raws.indexOf(r), new EditionModel.Raw(r.key, c == null ? "" : c.title, r.words, r.ideas));
        }
        Collections.sort(m.raws, (a, b) -> Integer.compare(indexOf(order, a.key), indexOf(order, b.key)));
        final List<String> tidiedTitles = new ArrayList<>();
        for (EditionModel.Raw r : m.raws) {
            if (m.tidied.contains(r.key)) {
                tidiedTitles.add(r.title);
            }
        }
        m.tidied.clear();
        m.tidied.addAll(tidiedTitles);

        // nothing of the reader's: no edition, whatever the skeleton says
        if (m.chapters > 0) {
            final List<Block> skeleton = skeleton(SkeletonData.fromJson(book.get(SkeletonData.KEY)), outline, w);
            if (!skeleton.isEmpty()) {
                m.sections.add(new EditionModel.Section(EditionModel.Part.SKELETON, w.sectionSkeleton,
                        Collections.singletonList(new EditionModel.Group("", "", skeleton))));
            }
        }
        add(m, EditionModel.Part.WORDS, w.sectionWords, words);
        add(m, EditionModel.Part.WRONG, w.sectionWrong, wrong);
        add(m, EditionModel.Part.CONNECTIONS, w.sectionConnections, connections);
        add(m, EditionModel.Part.OPEN, w.sectionOpen, open);
        kind(m, recalls, w.kindRecalls);
        kind(m, misses, w.kindMisses);
        kind(m, experiments, w.kindExperiments);
        kind(m, links, w.kindLinks);
        kind(m, questions, w.kindQuestions);
        return m;
    }

    // ------------------------------------------------------------------ pieces

    private static void add(EditionModel m, EditionModel.Part part, String title, List<EditionModel.Group> groups) {
        if (!groups.isEmpty()) {
            m.sections.add(new EditionModel.Section(part, title, groups));
        }
    }

    private static void kind(EditionModel m, boolean has, String name) {
        if (has) {
            m.kinds.add(name);
        }
    }

    private static int indexOf(List<Chapter> order, String key) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).key.equals(key)) {
                return i;
            }
        }
        return order.size();
    }

    private static Chapter chapter(Map<String, Chapter> chapters, String key, String title) {
        final String k = key == null ? "" : key;
        Chapter c = chapters.get(k);
        if (c == null) {
            c = new Chapter(k);
            chapters.put(k, c);
        }
        if (c.title.isEmpty() && title != null) {
            // as stored: a title is never changed
            c.title = title.trim();
        }
        return c;
    }

    /** One missed or half-remembered idea: what it was, the book's words for it, how its reviews went. */
    private static void miss(Chapter c, EditionWords w, boolean missed, String idea, String quote, Review r, String today) {
        final String text = one(idea);
        if (text.isEmpty()) {
            return;
        }
        final StringBuilder out = new StringBuilder(EditionWords.f(missed ? w.missed : w.unsure, sentence(text)));
        final String q = one(quote);
        if (!q.isEmpty()) {
            out.append(' ').append(EditionWords.f(w.bookSays, q));
        }
        boolean right = false;
        if (r != null) {
            r.used = true;
            final String since = rightSince(r.history);
            if (!since.isEmpty()) {
                out.append(' ').append(EditionWords.f(w.rightSince, EditionDates.shortDay(since, today, w.months)));
                right = true;
            } else if (!r.history.isEmpty()) {
                out.append(' ').append(EditionWords.f(w.notYet,
                        EditionDates.shortDay(r.history.get(r.history.size() - 1).day, today, w.months)));
            }
        }
        c.wrong.add(out.toString());
        c.listed.add(norm(text));
        if (c.missLead.isEmpty()) {
            c.missLead = EditionWords.f(missed ? w.missedShort : w.unsureShort, cut(text, 48));
            c.missRight = right;
        }
    }

    /**
     * The day from which every review of an idea was right: the first review after the last wrong
     * one. Empty when it was never reviewed or the last review was wrong.
     */
    static String rightSince(List<Shown> history) {
        String since = "";
        for (Shown s : history) {
            if (!s.right) {
                since = "";
            } else if (since.isEmpty()) {
                since = s.day;
            }
        }
        return since;
    }

    /** "Week-long roles. Task switches: 9 → 4; tickets: 31 → 38. It held. Boredom went up too." */
    private static String tried(UseExperiment x, EditionWords w) {
        final StringBuilder out = new StringBuilder(sentence(one(x.tryText)));
        final StringBuilder measures = new StringBuilder();
        for (UseExperiment.Measure m : x.measures) {
            if (m.hasBoth() && !m.name.trim().isEmpty()) {
                measures.append(measures.length() > 0 ? "; " : "").append(EditionWords.f(w.measure, m.label(),
                        UseExperiment.number(m.before), UseExperiment.number(m.during)));
            }
        }
        if (measures.length() > 0) {
            out.append(' ').append(measures).append('.');
        }
        final String verdict = UseExperiment.HELD.equals(x.verdict) ? w.verdictHeld
                : UseExperiment.MOSTLY.equals(x.verdict) ? w.verdictMostly
                : UseExperiment.DIDNT.equals(x.verdict) ? w.verdictDidnt : "";
        if (!verdict.isEmpty()) {
            out.append(' ').append(verdict);
        }
        final String surprise = one(x.surprise);
        if (!surprise.isEmpty()) {
            out.append(' ').append(sentence(surprise));
        }
        return out.toString();
    }

    /** The skeleton's blocks: the one line, then the parts' lines, and the chapters' in a book of few chapters. */
    private static List<Block> skeleton(SkeletonData d, EditionOutline outline, EditionWords w) {
        final List<Block> out = new ArrayList<>();
        final String line = one(d.line.isEmpty() ? d.paragraph : d.line);
        if (line.isEmpty()) {
            return out;
        }
        out.add(new Block(Kind.LINE, w.labelLine, "", line, ""));
        if (outline.isEmpty()) {
            final String paragraph = one(d.paragraph);
            if (!paragraph.isEmpty() && !paragraph.equals(line)) {
                out.add(new Block(Kind.PARAGRAPH, "", "", paragraph, ""));
            }
            return out;
        }
        int partLines = 0;
        int chapterLines = 0;
        for (EditionOutline.Entry e : outline.entries) {
            if (e.part && !one(d.partLines.get(e.key)).isEmpty()) {
                partLines++;
            } else if (!e.part && !one(d.chapterLines.get(e.key)).isEmpty()) {
                chapterLines++;
            }
        }
        final boolean withChapters = partLines == 0 || chapterLines <= FEW_CHAPTERS;
        for (EditionOutline.Entry e : outline.entries) {
            final String text = one(e.part ? d.partLines.get(e.key) : d.chapterLines.get(e.key));
            if (text.isEmpty() || e.title.isEmpty() || (!e.part && !withChapters)) {
                continue;
            }
            out.add(new Block(e.part ? Kind.PART : Kind.CHAPTER_LINE, "", e.title, text, ""));
        }
        return out;
    }

    // ------------------------------------------------------------------ tolerant reading

    private static Recall.Attempt attempt(JSONObject o) {
        try {
            return Recall.Attempt.fromJson(o);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static UseExperiment experiment(JSONObject o) {
        try {
            return UseExperiment.fromJson(o);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The review items by chapter key: {@code review:<chapter key>:<idea id>}, written by Spaced review. */
    private static Map<String, List<Review>> reviews(Map<String, JSONObject> book, TimeZone zone) {
        final Map<String, List<Review>> out = new LinkedHashMap<>();
        for (Map.Entry<String, JSONObject> e : sorted(book, REVIEW)) {
            final String rest = e.getKey().substring(REVIEW.length());
            final int at = rest.lastIndexOf(':');
            if (at <= 0) {
                continue;
            }
            final JSONObject o = e.getValue();
            final Review r = new Review();
            try {
                // the idea's id in its chapter: "4" for a key passage's idea, "r4" for one the recall's check picked
                final String id = rest.substring(at + 1).trim();
                r.id = Integer.parseInt(id.startsWith("r") ? id.substring(1) : id);
            } catch (NumberFormatException ex) {
                r.id = -1;
            }
            r.idea = one(any(o, "idea", "text"));
            r.title = any(o, "chapterTitle", "chapter_title", "chapter title", "title").trim();
            r.reason = any(o, "reason", "why").toLowerCase(Locale.ROOT);
            final JSONArray h = o.optJSONArray("history");
            for (int i = 0; h != null && i < h.length(); i++) {
                final JSONObject s = h.optJSONObject(i);
                final String day = s == null ? "" : day(s, zone);
                // "added" only says when the idea entered the queue: it is no answer, right or wrong
                if (!day.isEmpty() && !"added".equalsIgnoreCase(s.optString("kind", "").trim())) {
                    r.history.add(new Shown(day, truth(s.opt("right"), s.opt("ok"), s.opt("correct"))));
                }
            }
            Collections.sort(r.history, (a, b) -> a.day.compareTo(b.day));
            List<Review> list = out.get(rest.substring(0, at));
            if (list == null) {
                list = new ArrayList<>();
                out.put(rest.substring(0, at), list);
            }
            list.add(r);
        }
        return out;
    }

    private static Review review(List<Review> known, int id, String idea) {
        if (known == null) {
            return null;
        }
        final String n = norm(idea);
        for (Review r : known) {
            if (!r.idea.isEmpty() && norm(r.idea).equals(n)) {
                return r;
            }
        }
        for (Review r : known) {
            // else by its number: the review of a chapter's idea carries the idea's id in its key
            if (r.id == id && !r.used) {
                return r;
            }
        }
        return null;
    }

    /** 2 for a reason that says the idea was missed, 1 for half remembered, 0 for any other reason. */
    private static int missKind(String reason) {
        if (reason.contains("unsure") || reason.contains("shaky") || reason.contains("half") || reason.contains("partly")) {
            return 1;
        }
        return reason.contains("miss") || reason.contains("wrong") || reason.contains("forgot") || reason.contains("gap")
                ? 2 : 0;
    }

    /** A review's day: a {@code yyyy-MM-dd} text, or a time in ms. */
    private static String day(JSONObject o, TimeZone zone) {
        for (String name : new String[]{"day", "date", "at"}) {
            final Object v = o.opt(name);
            if (v instanceof String && UseDates.isDay(((String) v).trim())) {
                return ((String) v).trim();
            }
            if (v instanceof Number && ((Number) v).longValue() > 0) {
                return UseDates.of(((Number) v).longValue(), zone);
            }
        }
        return "";
    }

    private static boolean truth(Object... values) {
        for (Object v : values) {
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            if (v instanceof Number) {
                return ((Number) v).intValue() != 0;
            }
            if (v instanceof String) {
                final String s = ((String) v).trim().toLowerCase(Locale.ROOT);
                return s.equals("true") || s.equals("right") || s.equals("yes") || s.equals("1");
            }
        }
        return false;
    }

    /** Whether a side of a connection is this book. */
    private static boolean isBook(JSONObject side, String bookName) {
        if (side == null || bookName == null) {
            return false;
        }
        for (String name : new String[]{"name", "bookName", "book_name", "book", "file"}) {
            if (bookName.equals(side.optString(name, null))) {
                return true;
            }
        }
        return false;
    }

    private static String relation(String relation, EditionWords w) {
        final String r = relation == null ? "" : relation.trim().toLowerCase(Locale.ROOT);
        return r.startsWith("same") ? w.same : r.startsWith("disagree") ? w.disagrees : r.startsWith("extend")
                ? w.extendsIdea : "";
    }

    private static String settled(String code, EditionWords w) {
        final String c = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        return c.contains("establish") ? w.settledEstablished : c.contains("debat") ? w.settledDebated
                : c.contains("specul") ? w.settledSpeculative : "";
    }

    /** The first of the named fields that holds text, or empty. */
    static String any(JSONObject o, String... names) {
        if (o != null) {
            for (String name : names) {
                final Object v = o.opt(name);
                if (v instanceof String && !((String) v).trim().isEmpty()) {
                    return (String) v;
                }
            }
        }
        return "";
    }

    private static List<String> strings(JSONArray a) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            final String s = one(a.optString(i, ""));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** The items under a prefix in key order (the store's own order differs between devices). */
    private static List<Map.Entry<String, JSONObject>> sorted(Map<String, JSONObject> items, String prefix) {
        final List<Map.Entry<String, JSONObject>> out = new ArrayList<>();
        for (Map.Entry<String, JSONObject> e : items.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(prefix) && e.getValue() != null
                    && !e.getValue().optBoolean("del")) {
                out.add(e);
            }
        }
        Collections.sort(out, (a, b) -> a.getKey().compareTo(b.getKey()));
        return out;
    }

    // ------------------------------------------------------------------ text

    /** A chapter the book's contents do not name: by its key. */
    static String titleOf(String key, EditionWords w) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.equals("@front")) {
            return w.front;
        }
        if (key.startsWith("@pages:")) {
            try {
                return EditionWords.f(w.pagesFrom, String.valueOf(Integer.parseInt(key.substring(7).trim()) + 1));
            } catch (NumberFormatException e) {
                return "";
            }
        }
        final int slash = key.lastIndexOf('/');
        final String name = slash >= 0 ? key.substring(slash + 1) : key;
        final int hash = name.lastIndexOf('#');
        return hash > 0 ? name.substring(0, hash) : name;
    }

    private static String one(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String norm(String s) {
        return one(s).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}]+$", "");
    }

    /** The text closed with a full stop when it ends without one. */
    static String sentence(String s) {
        final String t = one(s);
        if (t.isEmpty()) {
            return t;
        }
        final char last = t.charAt(t.length() - 1);
        return last == '.' || last == '!' || last == '?' || last == '…' || last == '”' || last == '"' || last == ':'
                ? t : t + ".";
    }

    /** At most {@code max} characters, cut at a word's end with "…". */
    static String cut(String s, int max) {
        final String t = one(s);
        if (t.length() <= max) {
            return t;
        }
        int end = t.lastIndexOf(' ', max - 1);
        if (end < max / 2) {
            end = max - 1;
        }
        return t.substring(0, end).trim() + "…";
    }

    private static String join(String separator, String... parts) {
        final StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                out.append(out.length() > 0 ? separator : "").append(p);
            }
        }
        return out.toString();
    }
}
