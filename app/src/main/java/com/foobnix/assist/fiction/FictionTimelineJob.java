package com.foobnix.assist.fiction;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.pdf.info.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds or updates the timeline and the places of a story up to the reader's page (task kind
 * {@code AiTask.FICTION_TIMELINE}), by the same rule as the who's who: only the text of the
 * {@link FictionShield.Window} is read and sent, one request when it fits. An update sends only
 * the text between the stored place and the page, with the stored timeline in short.
 * <p>
 * The stored who's who lends the request its names, but only when it was built from text no
 * later than the reader's page, and only the people met by the part that is sent. Each quote of
 * the answer is looked for in the pages that were sent ({@link FictionQuotes}); one that is not
 * there is dropped and its moment kept without it.
 */
public final class FictionTimelineJob extends FictionJob {

    private FictionTimeline timeline = new FictionTimeline();
    private FictionCast cast;

    public FictionTimelineJob(Context context, AiJobs.Target target, BookChapters.Range ticket, long redoBefore) {
        super(context, target, ticket, redoBefore);
    }

    @Override
    protected int begin(FictionChapters chapters, int pages) {
        final JSONObject item = FictionStore.timelineItem(target.name);
        if (item == null || (redoBefore > 0 && FictionStore.time(item) < redoBefore)) {
            timeline = new FictionTimeline();
            return 0;
        }
        timeline = FictionTimeline.fromJson(item);
        // the pages of its quotes, in the layout the text is read in now
        timeline.rescale(timeline.mark.pages, pages);
        return timeline.mark.endIn(pages);
    }

    @Override
    protected boolean block(BookText book, FictionChapters chapters, FictionBlocks.Block block, String[] plain,
                            String text, int part, int parts, int end) throws Exception {
        final boolean update = !timeline.mark.isEmpty();
        final String system = AiPrompts.withMaster(settings.masterPrompt,
                FictionPrompts.timelineSystem(settings.answerLanguage, update) + "\n" + AiPrompts.SIDE_MATTER);
        final String answer = ask(system, FictionPrompts.timelineUser(target.title, names(chapters, plain.length, block),
                update ? timeline : null, part, parts, text), null);
        final FictionTimeline parsed = FictionTimeline.parse(answer, chapters, FictionChapters.n(block.lastChapter()));
        if (parsed == null) {
            failWith(string(wasCutOff() ? R.string.ai_cut_off : R.string.canvas_t29_unreadable));
            return false;
        }
        if (!parsed.story && timeline.isEmpty()) {
            failWith(string(R.string.canvas_t30_not_a_story));
            return false;
        }
        checkCancelled();
        // a quote counts only when the pages that were sent hold it
        new FictionQuotes(plain, block.from(), block.to()).check(parsed);
        timeline.merge(parsed);
        if (timeline.isEmpty() && part == parts) {
            failWith(string(R.string.canvas_t30_nothing));
            return false;
        }
        timeline.mark = FictionMark.at(chapters, end);
        timeline.model = settings.model;
        FictionStore.saveTimeline(target.name, timeline);
        return true;
    }

    /** The names the request may carry: none from a who's who that knows more than the reader has read. */
    private List<String> names(FictionChapters chapters, int pages, FictionBlocks.Block block) {
        if (cast == null) {
            cast = FictionStore.loadCast(target.name);
        }
        if (cast.isEmpty() || cast.mark.endIn(pages) > limit) {
            return new ArrayList<>();
        }
        return FictionPrompts.names(cast, chapters, block.lastChapter());
    }

    @Override
    protected void nothingNew(FictionChapters chapters, int end) throws Exception {
        if (!timeline.isEmpty()) {
            timeline.mark = FictionMark.at(chapters, end);
            FictionStore.saveTimeline(target.name, timeline);
        }
    }

    @Override
    protected String asking() {
        return string(R.string.canvas_t30_asking);
    }

    @Override
    protected String upToDate() {
        return string(R.string.canvas_t30_up_to_date);
    }

    @Override
    protected String done() {
        return string(R.string.canvas_t30_ready, timeline.moments.size(), timeline.places.size());
    }
}
