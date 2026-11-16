package com.foobnix.assist.prequestions;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.ArrayList;
import java.util.List;

/**
 * One request on a chapter's text: three questions to guess before reading it, each tied to the
 * sentence of the book that answers it ({@link PreQ#place}). Sent only when the reader taps
 * "Make my guesses" (Assist.PRE_QUESTIONS on); a screen job, cancelled with the reader.
 */
final class PreQJob extends AiJobs.Job {

    /** Characters of a chapter sent at most: its answers are in the part sent. */
    static final int MAX_CHARS = 40_000;
    /** Less text than this makes no questions worth guessing. */
    static final int MIN_CHARS = 600;

    interface Screen {
        /** @param questions the questions found in the book; empty when the chapter has too little text */
        void onDone(List<PreQ.Question> questions);

        void onError(String message);

        void onNeedSettings();
    }

    private final BookChapters.Range range;
    private final Screen screen;

    PreQJob(Activity activity, AiJobs.Target target, BookChapters.Range range, Screen screen) {
        super(activity, target);
        this.range = range;
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void work() throws Exception {
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final List<String> words = new ArrayList<>();
        final List<Integer> wordPage = new ArrayList<>();
        final StringBuilder plain = new StringBuilder();
        final int max = Math.min(MAX_CHARS, Math.max(MIN_CHARS, settings.maxChars - 4000));
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
        if (plain.length() < MIN_CHARS) {
            ui(() -> screen.onDone(new ArrayList<PreQ.Question>()));
            return;
        }
        final String system = AiPrompts.withMaster(settings.masterPrompt, PreQ.system(settings.answerLanguage));
        final String answer = ask(system, PreQ.user(target.title, chapterTitle(range), plain.toString()), null);
        checkCancelled();
        final List<PreQ.Question> parsed = PreQ.parse(answer);
        if (parsed.isEmpty()) {
            failWith(string(R.string.preq_unreadable));
            return;
        }
        final List<PreQ.Question> kept = PreQ.place(parsed, words, wordPage);
        ui(() -> screen.onDone(kept));
    }
}
