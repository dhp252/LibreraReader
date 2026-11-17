package com.foobnix.assist.fiction;

import android.content.Context;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.assist.Assist;
import com.foobnix.pdf.info.R;

import java.util.List;

/**
 * A spoiler-safe request over the book's text up to the reader's page, as a long AI task (F-004
 * D-151): the pages of the {@link FictionShield.Window} are read on the device and sent in order,
 * in one request when they fit the character cap, else block by block ({@link FictionBlocks}),
 * each block's result saved before the next is asked, so a task that is stopped goes on from the
 * block it saved last.
 * <p>
 * The reader's page comes with the task as a ticket ({@link FictionShield#ticket}), taken when
 * the reader tapped; nothing the job reads or sends lies after it, also when the reader has
 * turned pages since. With {@link Assist#FICTION} off the job sends and writes nothing.
 */
abstract class FictionJob extends AiJobs.Job {

    /** What the screen shows while the task runs; called on the main thread. */
    public interface Screen {
        void onStatus(String status);
    }

    /** All the job may read. */
    private final BookChapters.Range ticket;
    /** Not 0: data stored before this time is built again from the start of the book. */
    protected final long redoBefore;

    /**
     * The first page after the reader's, in the job's layout: set before the first block. Stored
     * data built from text after it must not go into a request.
     */
    protected int limit;

    private volatile Screen screen;
    private volatile String status = "";

    FictionJob(Context context, AiJobs.Target target, BookChapters.Range ticket, long redoBefore) {
        super(context, target);
        this.ticket = ticket;
        this.redoBefore = redoBefore;
    }

    @Override
    protected void setTaskScreen(Object screen) {
        this.screen = screen instanceof Screen ? (Screen) screen : null;
        if (this.screen != null && !status.isEmpty()) {
            status(status);
        }
    }

    @Override
    protected void showStatus(String text) {
        status(text);
    }

    protected void status(String text) {
        status = text;
        step(text, 0, 0);
        ui(() -> {
            final Screen s = screen;
            if (s != null) {
                s.onStatus(text);
            }
        });
    }

    @Override
    protected void failed(String message) {
        // a task: AiProgress tells the screen how it ended, the notification says it otherwise
    }

    /**
     * Loads what is stored and says where it stops, in a layout of {@code pages} pages: the first
     * page the job still has to read. 0 for nothing stored (or a redo).
     */
    protected abstract int begin(FictionChapters chapters, int pages);

    /**
     * Asks for one block and saves what it gave, marked as built up to {@code end}.
     *
     * @param text the block's text ({@link FictionBlocks#text})
     * @param part which block this is, from 1, of {@code parts}
     * @return false when the job reported a failure and must stop
     */
    protected abstract boolean block(BookText book, FictionChapters chapters, FictionBlocks.Block block, String[] plain,
                                     String text, int part, int parts, int end) throws Exception;

    /** There is no new text since the stored place: the stored data now also stands for {@code end}. */
    protected abstract void nothingNew(FictionChapters chapters, int end) throws Exception;

    /** What the done notification says. */
    protected abstract String done();

    /** The status while the one request is out. */
    protected String asking() {
        return string(R.string.canvas_t29_asking);
    }

    /** What the task says when there is no text between the stored place and the page. */
    protected String upToDate() {
        return string(R.string.canvas_t29_up_to_date);
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.FICTION.isOn()) {
            // switched off since the tap: nothing is sent or written
            failWith(string(R.string.canvas_t29_switched_off));
            return;
        }
        status(string(R.string.ai_reading_book));
        try (BookText text = open()) {
            final int pages = text.pageCount();
            final FictionChapters chapters = FictionChapters.of(AiJobs.toc(target, text), pages);
            final int from = begin(chapters, pages);
            // the one place the pages to read are decided
            final FictionShield.Window window = FictionShield.window(from, ticket, pages);
            if (window.end <= 0) {
                failWith(string(R.string.canvas_t29_no_place));
                return;
            }
            limit = window.end;
            if (window.isEmpty()) {
                result = upToDate();
                return;
            }
            final String[] plain = new String[pages];
            final int[] lengths = new int[pages];
            read(text, window.from, window.end, plain, lengths, this::status);
            final List<FictionBlocks.Block> blocks = FictionBlocks.blocks(chapters, window, lengths,
                    FictionPrompts.cap(settings.maxChars));
            if (blocks.isEmpty()) {
                if (window.from == 0) {
                    failWith(string(R.string.ai_no_text));
                } else {
                    nothingNew(chapters, window.end);
                    result = upToDate();
                }
                return;
            }
            for (int i = 0; i < blocks.size(); i++) {
                checkCancelled();
                final FictionBlocks.Block b = blocks.get(i);
                final String label = chapters.range(b.firstChapter(), b.lastChapter());
                final String s = blocks.size() == 1 ? asking()
                        : string(R.string.canvas_t29_asking_part, i + 1, blocks.size(), label);
                status(s);
                step(s, i + 1, blocks.size());
                final boolean last = i == blocks.size() - 1;
                if (!block(text, chapters, b, plain, FictionBlocks.text(chapters, window, b, plain), i + 1, blocks.size(),
                        last ? window.end : b.to())) {
                    return;
                }
            }
            result = done();
        }
    }
}
