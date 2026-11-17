package com.foobnix.assist.path;

import android.app.Activity;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiPrompts;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.skeleton.SkeletonData;
import com.foobnix.assist.skeleton.SkeletonParts;
import com.foobnix.pdf.info.R;

import java.util.ArrayList;
import java.util.List;

/**
 * "One topic, several books": one request with the topic and each chosen book's chapter lines
 * (its skeleton or study preview, only read); the merged path is stored in the library's assist
 * file under {@code topic:<slug>}. No book's text is sent; each book's table of contents is read
 * on the device for the order and titles of its chapters. Started from the topic screen, it stops
 * with it.
 */
final class TopicJob extends AiJobs.Job {

    /** What the screen hears, on the main thread. */
    interface Screen {
        void onStatus(String status);

        void onTopic(TopicData data);

        void onNeedSettings();

        void onError(String message);
    }

    private final String topic;
    private final List<TopicBooks.Item> items;
    private final Screen screen;

    /** @param target the open book: its layout is used to read the other books' contents */
    TopicJob(Activity activity, AiJobs.Target target, String topic, List<TopicBooks.Item> items, Screen screen) {
        super(activity, target);
        this.topic = topic;
        this.items = new ArrayList<>(items);
        this.screen = screen;
    }

    @Override
    protected void failed(String message) {
        ui(() -> screen.onError(message));
    }

    @Override
    protected void showStatus(String text) {
        status(text);
    }

    private void status(String text) {
        ui(() -> screen.onStatus(text));
    }

    @Override
    protected void work() throws Exception {
        if (!Assist.PATH.isOn()) {
            return;
        }
        if (!settings.isReady()) {
            ui(screen::onNeedSettings);
            return;
        }
        final List<TopicData.Book> books = new ArrayList<>();
        final List<List<PathChapters.Entry>> lists = new ArrayList<>();
        for (TopicBooks.Item item : items) {
            checkCancelled();
            status(string(R.string.canvas_t21_reading_contents, item.title));
            final SkeletonData lines = TopicBooks.lines(item.name);
            List<PathChapters.Entry> list = null;
            try (BookText text = BookText.open(item.path, target.width, target.height, target.fontSize)) {
                if (text != null) {
                    list = PathChapters.list(SkeletonParts.group(text.toc(), text.pageCount()), lines);
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            if (list == null || !PathChapters.hasLines(list)) {
                // the file cannot be read here: its lines alone
                list = TopicPrompts.fromLines(lines);
            }
            if (list.isEmpty()) {
                continue;
            }
            if (list.size() > TopicPrompts.MAX_CHAPTERS) {
                list = new ArrayList<>(list.subList(0, TopicPrompts.MAX_CHAPTERS));
            }
            books.add(item.book());
            lists.add(list);
        }
        if (books.size() < TopicData.MIN_BOOKS) {
            failWith(string(R.string.canvas_t21_too_few));
            return;
        }
        checkCancelled();
        status(string(R.string.canvas_t21_merging));
        final String answer = ask(AiPrompts.withMaster(settings.masterPrompt, TopicPrompts.system(settings.answerLanguage)),
                TopicPrompts.user(topic, books, lists, settings.maxChars), null);
        checkCancelled();
        final TopicPrompts.Answer parsed = TopicPrompts.parse(answer);
        final TopicData data = TopicData.fromAnswer(topic, parsed, books, lists, settings.model, System.currentTimeMillis());
        if (data.isEmpty()) {
            failWith(string(parsed.read ? R.string.canvas_t21_not_in_books
                    : wasCutOff() ? R.string.ai_cut_off : R.string.canvas_t20_unreadable));
            return;
        }
        AssistStore.get().put(AssistStore.LIBRARY, TopicData.key(data.topic), data.toJson());
        ui(() -> screen.onTopic(data));
    }
}
