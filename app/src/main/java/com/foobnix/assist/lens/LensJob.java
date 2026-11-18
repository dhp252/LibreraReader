package com.foobnix.assist.lens;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.assist.Assist;
import com.foobnix.pdf.info.R;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.List;

/**
 * The lens's requests, each one request started by a tap on its screen and stopped with it: a
 * chapter's bridge, the ladder of one of its ideas, and one selected sentence made concrete or
 * boiled down. Nothing runs with Assist.LENS off or without a lens.
 */
abstract class LensJob<T> extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen<T> {
        void onDone(T result);

        void onError(String message);

        void onNeedSettings();
    }

    final Screen<T> screen;
    final String author;
    LensProfile profile;
    LensProfile.Level level;

    LensJob(Activity activity, AiJobs.Target target, String author, Screen<T> screen) {
        super(activity, target);
        this.author = author;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.LENS.isOn()) {
            return;
        }
        if (!settings.isReady() && !fromClaude()) {
            ui(screen::onNeedSettings);
            return;
        }
        profile = LensStore.profile();
        if (!profile.isSet()) {
            failWith(string(R.string.lens_no_lens));
            return;
        }
        level = LensStore.level(target.name);
        final T result = make();
        if (result != null) {
            ui(() -> screen.onDone(result));
        }
    }

    /** The request and what it stores; null when it already reported a failure. */
    abstract T make() throws Exception;

    /** An answer an outside Claude wrote stands in for the request (com.foobnix.claude). */
    boolean fromClaude() {
        return false;
    }

    String system(String prompt) {
        return AiPrompts.withMaster(settings.masterPrompt, prompt);
    }

    /** The bridge of one chapter: from its stored key-passage ideas, else from its text (40k characters at most). */
    static final class Bridge extends LensJob<LensBridge> {
        private final BookChapters.Range range;

        Bridge(Activity activity, AiJobs.Target target, String author, BookChapters.Range range,
               Screen<LensBridge> screen) {
            super(activity, target, author, screen);
            this.range = range;
        }

        @Override
        boolean fromClaude() {
            return claudeWaiting(com.foobnix.claude.ClaudeFeature.LENS, range.key);
        }

        @Override
        LensBridge make() throws Exception {
            final AiStore.Chapter stored = AiStore.get().load(target.name).chapter(range.key);
            // Claude answers from the chapter's text (with quotes), never from its ideas
            final boolean claude = fromClaude();
            final List<LensIdea> ideas = claude ? new ArrayList<LensIdea>() : LensStore.ideas(stored);
            final boolean fromIdeas = !ideas.isEmpty();
            final List<String> words = new ArrayList<>();
            final List<Integer> wordPage = new ArrayList<>();
            final String user;
            if (fromIdeas) {
                user = LensPrompts.bridgeUser(target.title, author, range.title, ideas);
            } else {
                final StringBuilder plain = new StringBuilder();
                final int max = claude ? Integer.MAX_VALUE
                        : Math.min(LensPrompts.MAX_CHARS, Math.max(LensPrompts.MIN_CHARS, settings.maxChars - 4000));
                try (BookText text = open()) {
                    final int from = Math.max(0, range.from);
                    final int to = Math.min(text.pageCount(), range.to);
                    for (int p = from; p < to && plain.length() < max; p++) {
                        checkCancelled();
                        final TextWord[][] page = text.body(p);
                        for (String w : BookText.words(page)) {
                            words.add(w);
                            wordPage.add(p);
                        }
                        final String s = BookText.plain(page);
                        if (!s.isEmpty()) {
                            if (plain.length() > 0) {
                                plain.append("\n\n");
                            }
                            plain.append(s);
                        }
                    }
                }
                if (plain.length() < LensPrompts.MIN_CHARS) {
                    failWith(string(R.string.lens_no_text));
                    return null;
                }
                if (plain.length() > max) {
                    plain.setLength(max);
                }
                user = LensPrompts.bridgeUser(target.title, author, range.title, plain.toString());
            }
            final String answer = askFor(com.foobnix.claude.ClaudeFeature.LENS, range.key,
                    system(LensPrompts.bridgeSystem(profile, level, settings.answerLanguage, fromIdeas)), user, null);
            checkCancelled();
            final LensBridge bridge = LensBridge.parse(answer);
            if (bridge == null) {
                failWith(string(R.string.lens_unreadable));
                return null;
            }
            if (fromIdeas) {
                bridge.attach(ideas);
                bridge.ideasTime = stored.time;
            } else {
                final QuoteMatcher.Words all = new QuoteMatcher.Words(words);
                for (LensBridge.Row row : bridge.rows) {
                    final int[] at = row.quote.isEmpty() ? null : QuoteMatcher.locate(all, row.quote);
                    if (at == null) {
                        // not the book's words: nothing to jump to
                        row.quote = "";
                        row.page = -1;
                    } else {
                        row.quote = QuoteMatcher.join(words.subList(at[0], at[1] + 1));
                        row.page = wordPage.get(at[0]);
                    }
                    row.id = -1;
                }
            }
            bridge.title = range.title == null ? "" : range.title;
            bridge.fromIdeas = fromIdeas;
            bridge.lens = profile.signature(level);
            bridge.lensName = profile.shortName();
            bridge.model = settings.model;
            LensStore.saveBridge(target.name, range.key, bridge);
            return bridge;
        }
    }

    /** The four rungs of one row of a chapter's bridge; kept inside the row. */
    static final class Ladder extends LensJob<LensLadder> {
        private final String chapterKey;
        private final String chapterTitle;
        private final int index;
        private final LensBridge.Row row;

        Ladder(Activity activity, AiJobs.Target target, String author, String chapterKey, String chapterTitle, int index,
               LensBridge.Row row, Screen<LensLadder> screen) {
            super(activity, target, author, screen);
            this.chapterKey = chapterKey;
            this.chapterTitle = chapterTitle;
            this.index = index;
            this.row = row;
        }

        @Override
        LensLadder make() throws Exception {
            final String answer = ask(system(LensPrompts.ladderSystem(profile, level, settings.answerLanguage)),
                    LensPrompts.ladderUser(target.title, author, chapterTitle, row.idea, row.yours, row.quote), null);
            checkCancelled();
            final LensLadder ladder = LensLadder.parse(answer);
            if (ladder == null) {
                failWith(string(R.string.lens_unreadable));
                return null;
            }
            LensStore.saveLadder(target.name, chapterKey, index, row.idea, ladder);
            return ladder;
        }
    }

    /** One selected sentence made concrete or boiled down; shown, not kept. */
    static final class Sentence extends LensJob<String> {
        private final String sentence;
        private final boolean concrete;

        Sentence(Activity activity, AiJobs.Target target, String author, String sentence, boolean concrete,
                 Screen<String> screen) {
            super(activity, target, author, screen);
            this.sentence = sentence;
            this.concrete = concrete;
        }

        @Override
        String make() throws Exception {
            final String answer = ask(system(LensPrompts.sentenceSystem(profile, level, settings.answerLanguage, concrete)),
                    LensPrompts.sentenceUser(target.title, author, sentence), null);
            checkCancelled();
            final String text = LensPrompts.plain(answer);
            if (text.isEmpty()) {
                failWith(string(R.string.lens_unreadable));
                return null;
            }
            return text;
        }
    }
}
