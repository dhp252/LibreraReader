package com.foobnix.assist.fiction;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import org.json.JSONObject;

/**
 * Builds or updates the who's who of a story up to the reader's page (task kind {@code
 * AiTask.FICTION_CAST}). Nothing stored: the text from the start of the book. A cast stored for
 * an earlier place: only the text between that place and the page, with the stored cast. A cast
 * stored for a later place (the reader went back) is left as it is, unless the task is a redo.
 */
public final class FictionCastJob extends FictionJob {

    private FictionCast cast = new FictionCast();

    public FictionCastJob(Context context, AiJobs.Target target, BookChapters.Range ticket, long redoBefore) {
        super(context, target, ticket, redoBefore);
    }

    @Override
    protected int begin(FictionChapters chapters, int pages) {
        final JSONObject item = FictionStore.castItem(target.name);
        if (item == null || (redoBefore > 0 && FictionStore.time(item) < redoBefore)) {
            cast = new FictionCast();
            return 0;
        }
        cast = FictionCast.fromJson(item);
        return cast.mark.endIn(pages);
    }

    @Override
    protected boolean block(BookText book, FictionChapters chapters, FictionBlocks.Block block, String[] plain,
                            String text, int part, int parts, int end) throws Exception {
        final boolean update = !cast.mark.isEmpty();
        final String system = AiPrompts.withMaster(settings.masterPrompt,
                FictionPrompts.castSystem(settings.answerLanguage, update) + "\n" + AiPrompts.SIDE_MATTER);
        final String answer = ask(system, FictionPrompts.castUser(target.title, update ? cast : null, part, parts, text),
                null);
        final FictionCast parsed = FictionCast.parse(answer, chapters, FictionChapters.n(block.firstChapter()),
                FictionChapters.n(block.lastChapter()));
        if (parsed == null) {
            failWith(string(wasCutOff() ? R.string.ai_cut_off : R.string.canvas_t29_unreadable));
            return false;
        }
        if (!parsed.story && cast.isEmpty()) {
            failWith(string(R.string.canvas_t29_not_a_story));
            return false;
        }
        checkCancelled();
        cast.merge(parsed);
        if (cast.isEmpty() && part == parts) {
            failWith(string(R.string.canvas_t29_nobody));
            return false;
        }
        cast.mark = FictionMark.at(chapters, end);
        cast.model = settings.model;
        FictionStore.saveCast(target.name, cast);
        return true;
    }

    @Override
    protected void nothingNew(FictionChapters chapters, int end) throws Exception {
        if (!cast.isEmpty()) {
            cast.mark = FictionMark.at(chapters, end);
            FictionStore.saveCast(target.name, cast);
        }
    }

    @Override
    protected String done() {
        return string(R.string.canvas_t29_cast_ready, cast.people.size());
    }
}
