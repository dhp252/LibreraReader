package com.foobnix.claude;

import com.foobnix.ai.AiPrompts;
import com.foobnix.assist.lens.LensPrompts;
import com.foobnix.assist.pictures.PicturePrompts;
import com.foobnix.assist.prequestions.PreQ;
import com.foobnix.assist.updated.UpdatedPrompts;
import com.foobnix.assist.zoom.ZoomPrompts;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The skill an outside Claude reads ({@code <WebDAV root>/.claude/skills/yggdrasil-reading/}):
 * SKILL.md (shipped in the APK's assets), {@code formats.json} and one {@code features/<id>.md} per
 * feature, whose user-message templates come from the features' own prompt code, so they always
 * match the installed app. The system prompts are per book, in the desks ({@link ClaudeDesk}).
 * Pure: the upload is {@code ClaudeSync}'s.
 */
public final class ClaudeSkill {

    public static final String NAME = "yggdrasil-reading";
    /** Where the skill lives on the server, from the WebDAV root: Claude Code finds it there. */
    public static final String FOLDER = ".claude/skills/" + NAME;
    public static final String FORMATS = "formats.json";
    /** Raised when SKILL.md or a feature file changes in a way an older app must not undo. */
    public static final int VERSION = 1;

    static final String TITLE = "{{book.title}}";
    static final String AUTHOR = "{{book.author}}";
    static final String CHAPTER = "{{chapter.title}}";
    static final String TEXT = "{{the chapter's text from text.txt}}";

    private ClaudeSkill() {
    }

    /** Path in the skill folder → content. */
    public static Map<String, String> files(String skillMd, String appVersion) throws JSONException {
        final Map<String, String> out = new LinkedHashMap<>();
        out.put("SKILL.md", skillMd.replace("{{APP}}", "app version " + appVersion));
        out.put(FORMATS, formats(appVersion).toString(2));
        for (ClaudeFeature f : ClaudeFeature.values()) {
            out.put("features/" + f.id + ".md", feature(f));
        }
        return out;
    }

    /** formats.json: the skill's version and each feature's answer format. */
    public static JSONObject formats(String appVersion) throws JSONException {
        final JSONObject features = new JSONObject();
        for (ClaudeFeature f : ClaudeFeature.values()) {
            features.put(f.id, f.format);
        }
        return new JSONObject().put("skill", VERSION).put("app", appVersion).put("desk", ClaudeDesk.DESK_FORMAT)
                .put("features", features);
    }

    /**
     * Whether a formats.json found on the server comes from a newer app than this one (a higher
     * skill version, or a higher format of any feature): this app then leaves the skill alone.
     */
    public static boolean newerThanMine(String formatsJson) {
        if (formatsJson == null || formatsJson.trim().isEmpty()) {
            return false;
        }
        try {
            final JSONObject o = new JSONObject(formatsJson);
            if (o.optInt("skill") > VERSION) {
                return true;
            }
            final JSONObject features = o.optJSONObject("features");
            if (features == null) {
                return false;
            }
            for (ClaudeFeature f : ClaudeFeature.values()) {
                if (features.optInt(f.id) > f.format) {
                    return true;
                }
            }
            final java.util.Iterator<String> ids = features.keys();
            while (ids.hasNext()) {
                if (ClaudeFeature.of(ids.next()) == null) {
                    // a feature this app does not know
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            return false;
        }
    }

    static String feature(ClaudeFeature f) {
        final StringBuilder out = new StringBuilder();
        switch (f) {
            case IDEAS:
                head(out, "Key passages", "chapter",
                        "Every idea of the chapter with its category, its key sentences and the paragraphs to read "
                                + "in full. The app marks them in the book (and X-ray, Recall and Pictures build on them).");
                text(out, "The chapter's whole text. When book.json has a `goal`, the user message names it.");
                user(out, AiPrompts.ideas(TITLE, CHAPTER, TEXT, "{{book.goal, or leave this line out when empty}}"));
                notes(out, "Categories: only the codes listed in the prompt (book.json `categories` lists every code "
                                + "the app knows). Without a goal never use \"apply\".",
                        "Sentences and paragraph start/end words are looked up in the book: copy them exactly from text.txt.",
                        "Write the whole chapter in one answer, however long: the app does not split it for you.");
                break;
            case SUMMARY:
                head(out, "Summary", "chapter, or `book` (chapter \"@book\") for the whole book",
                        "A chapter's summary, or the whole book's.");
                text(out, "A chapter: its whole text. The book: the text of every chapter that is not side matter.");
                user(out, AiPrompts.chapterSummary(TITLE, CHAPTER, TEXT));
                out.append("For the whole book:\n\n```\n")
                        .append(AiPrompts.bookSummary(TITLE, "{{every chapter's text from text.txt, side matter left out}}"))
                        .append("\n```\n\n");
                notes(out, "The answer is plain prose (a JSON string in the answer file), no Markdown.");
                break;
            case SKELETON:
                head(out, "Skeleton first", "`book` (chapter \"@book\")",
                        "The book's thesis, its argument in a paragraph, a line per part and per chapter, and where "
                                + "the conclusion is.");
                text(out, "book.json `skeleton`: `parts` (each with its `n`, `title` and the chapter numbers it holds) "
                        + "and `chapters` (the skeleton's own chapter list, with `n`, `title` and pages `from`-`to`). "
                        + "Read each of those chapters' opening and ending in text.txt (by page), or all of it.");
                out.append("User message: the book title, the parts as `<part n=\"0\" title=\"...\" chapters=\"0-3\"/>` "
                        + "lines when the book has parts, then for each skeleton chapter its n, title, length in "
                        + "words, opening and ending.\n\n");
                notes(out, "Number parts and chapters by book.json `skeleton`, not by `chapters`: the two lists can differ.",
                        "The prompt in prompts/skeleton.txt says whether the book has parts.");
                break;
            case PRE_QUESTIONS:
                head(out, "Pre-questions", "chapter",
                        "Three questions the reader guesses before reading the chapter; each answer is a sentence of it.");
                text(out, "The chapter's whole text.");
                user(out, PreQ.user(TITLE, CHAPTER, TEXT));
                notes(out, "Answer sentences are looked up in the book: copy them exactly.");
                break;
            case XRAY:
                head(out, "X-ray: rate the support", "chapter",
                        "How well each idea of the chapter is supported, and its weak spot.");
                text(out, "Not the text: the chapter's key passages. Open `profile.<name>/ai/<book>.json`, find the "
                        + "entry of `highlights` whose `key` is the chapter's key, and list its `ideas` (id, category, "
                        + "parent, text) with the `text` of each `quotes` and `paragraphs` entry whose `idea` is that id "
                        + "(at most 3 per idea). No ideas for the chapter: write key passages first and let the app take "
                        + "them (open the book), then rate.");
                out.append("User message:\n\n```\nBook: ").append(TITLE).append("\nChapter: ").append(CHAPTER)
                        .append("\n\nIdeas:\n1 [main]: <idea text>\n   \"<quote>\"\n2 [evidence] supports 1: <idea text>\n"
                                + "   \"<quote>\"\n```\n\n");
                notes(out, "Use the ids as they are in ai/<book>.json: the app drops ratings of ids it does not have.");
                break;
            case LENS:
                head(out, "Lens: concept bridge", "chapter",
                        "The chapter's main ideas, each beside its twin in the reader's own field.");
                text(out, "The chapter's whole text. The reader's fields and level are already in prompts/lens.txt "
                        + "(no such file: the reader has not set up Lens, write nothing).");
                user(out, LensPrompts.bridgeUser(TITLE, AUTHOR, CHAPTER, TEXT));
                notes(out, "Give each pair the quote form (author, quote, yours), never the id form: the app reads "
                        + "Claude's bridges from the text.");
                break;
            case ZOOM:
                head(out, "Semantic zoom", "chapter", "The chapter as Lean, Claims and one line.");
                text(out, "The chapter's whole text. When book.json has `zoomGoal`, the user message states it.");
                user(out, ZoomPrompts.user(TITLE, CHAPTER, TEXT, false, "{{book.zoomGoal, or leave this line out}}"));
                break;
            case UPDATED:
                head(out, "The book, updated", "chapter",
                        "Up to 6 notes on what changed since the book was written, how settled each is, a source, "
                                + "and the strongest objection.");
                text(out, "The chapter's whole text; book.json `author` and `year`.");
                user(out, UpdatedPrompts.user(TITLE, AUTHOR, 2001, CHAPTER, TEXT).replace("2001", "{{book.year}}"));
                notes(out, "Give a real source or say none is given, as the prompt asks; never invent one.");
                break;
            case PICTURES:
                head(out, "Prose to pictures", "chapter",
                        "The chapter drawn as a process, a table or a causal loop, each from a quote of the text.");
                text(out, "The chapter's whole text.");
                user(out, PicturePrompts.user(TITLE, CHAPTER, PicturePrompts.Sent.TEXT, TEXT));
                notes(out, "Quotes and numbers are checked against the chapter's text: copy them exactly.");
                break;
            default:
                break;
        }
        out.append("Answer file: `answers/").append(f.id).append("~<n>.json` with `\"feature\": \"").append(f.id)
                .append("\"` and `\"format\": ").append(f.format).append("`. System prompt: the desk's `prompts/")
                .append(f.id).append(".txt` (no file: the feature is off for this reader, write nothing).\n");
        return out.toString();
    }

    private static void head(StringBuilder out, String title, String per, String what) {
        out.append("# ").append(title).append("\n\n").append(what).append("\n\nOne answer per: ").append(per).append(".\n\n");
    }

    private static void text(StringBuilder out, String text) {
        out.append("Input: ").append(text).append("\n\n");
    }

    private static void user(StringBuilder out, String template) {
        out.append("User message (the app's own words; fill in the {{...}} parts):\n\n```\n").append(template)
                .append("\n```\n\n");
    }

    private static void notes(StringBuilder out, String... notes) {
        for (String n : notes) {
            out.append("- ").append(n).append('\n');
        }
        out.append('\n');
    }
}
