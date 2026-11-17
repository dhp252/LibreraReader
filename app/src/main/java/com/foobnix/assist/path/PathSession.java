package com.foobnix.assist.path;

import android.app.Activity;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.skeleton.SkeletonParts;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;
import com.foobnix.stats.ReadingTracker;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

/**
 * The shortest path in the open book while reading (Assist.PATH): when a page turn takes the
 * reader out of a chapter of the path, the chapter is flagged as read and a small card offers the
 * next one ("Next on your path"), never forced: it goes with ✕, "Not now" or the next page turn.
 * One session at a time, for the book shown; {@link #pageShown} is its only hook. A book without
 * a stored path costs one small file read when it opens and nothing after.
 */
public final class PathSession {

    private static PathSession current;

    private final String path;
    private final String name;
    private WeakReference<DocumentController> dcRef;
    private WeakReference<Activity> ownerRef;

    /** The book's path; null until read, empty when it has none. */
    private PathData data;
    /** The chapters in the reader's layout; null until read. */
    private Map<String, int[]> ranges;
    private int pageCount;
    private String rangesLayout = "";
    private boolean readingRanges;
    private int prevFrom = -1;
    private int prevLast = -1;
    private WeakReference<View> card = new WeakReference<>(null);
    /** The view page the card was shown on. */
    private int cardPage;

    private PathSession(DocumentController dc, String path) {
        this.path = path;
        this.name = AiHighlights.bookName(path);
        attach(dc);
    }

    // ------------------------------------------------------------------ the hook

    /** The reader shows a page (DocumentController.saveCurrentPage). */
    public static void pageShown(DocumentController dc) {
        try {
            if (!Assist.PATH.isOn()) {
                if (current != null) {
                    // switched off while reading
                    current.removeCard();
                    current = null;
                }
                return;
            }
            if (dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0 || dc.getActivity() == null) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc));
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current == null || !current.path.equals(path)) {
                if (current != null) {
                    current.removeCard();
                }
                current = new PathSession(dc, path);
                current.load(dc);
            } else if (current.dc() != dc) {
                // the same book in a re-created reader: the card lived in the old one
                current.removeCard();
                current.attach(dc);
                current.prevFrom = -1;
                current.prevLast = -1;
            }
            current.onPage(dc);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /**
     * A path screen made or changed the book's path: the session reads on with it.
     *
     * @param ranges the chapters in the reader's layout when the screen knows them, else null
     */
    static void changed(DocumentController dc, PathData data, Map<String, int[]> ranges, int pageCount) {
        if (dc == null || dc.getCurrentBook() == null || current == null
                || !current.path.equals(dc.getCurrentBook().getPath())) {
            return;
        }
        current.data = copy(data);
        if (ranges != null) {
            current.ranges = ranges;
            current.pageCount = pageCount;
            current.rangesLayout = layoutOf(dc);
        }
        current.removeCard();
    }

    // ------------------------------------------------------------------ the book's chapters

    /** The chapters of a book in a layout. */
    static final class Ranges {
        final Map<String, int[]> byKey;
        final int pageCount;

        Ranges(Map<String, int[]> byKey, int pageCount) {
            this.byKey = byKey;
            this.pageCount = pageCount;
        }
    }

    /** Reads the table of contents in the layout of {@code t}; null when the book cannot be read. Off the main thread. */
    static Ranges read(AiJobs.Target t) {
        try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
            if (text == null) {
                return null;
            }
            final List<SkeletonParts.Part> parts = SkeletonParts.group(AiJobs.toc(t, text), text.pageCount());
            return new Ranges(PathChapters.ranges(parts), text.pageCount());
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    /** Goes to a page of the book (0-based, the layout the chapters were read in). */
    static void goTo(DocumentController dc, int page) {
        try {
            dc.onGoToPage(ReadingTracker.viewPage(dc, Math.max(0, page) + 1));
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The first and last book page (0-based) the reader shows. */
    static int[] shown(DocumentController dc) {
        final int[] real = ReadingTracker.realPages(dc, dc.getCurentPageFirst1());
        final int from = real[0] - 1;
        return new int[]{from, Math.max(from, real[1] - 1)};
    }

    /** What a layout is known by: a reflowing book laid out anew has its chapters on other pages. */
    static String layoutOf(DocumentController dc) {
        final AiJobs.Target t = AiJobs.Target.of(dc);
        return t.width + "x" + t.height + "|" + t.fontSize + "|" + ReadingTracker.realPageCount(dc);
    }

    // ------------------------------------------------------------------ inside

    private void attach(DocumentController dc) {
        dcRef = new WeakReference<>(dc);
        ownerRef = new WeakReference<>(dc.getActivity());
    }

    private DocumentController dc() {
        return dcRef.get();
    }

    private Activity owner() {
        final Activity a = ownerRef.get();
        return a == null || Apps.isDestroyedActivity(a) ? null : a;
    }

    private static PathData copy(PathData data) {
        try {
            return data == null ? new PathData() : PathData.fromJson(data.toJson());
        } catch (Exception e) {
            return new PathData();
        }
    }

    private void post(Runnable r) {
        final Activity a = owner();
        if (a != null) {
            a.runOnUiThread(() -> {
                if (current == this && owner() != null) {
                    r.run();
                }
            });
        }
    }

    private void load(DocumentController dc) {
        final AiJobs.Target target = AiJobs.Target.of(dc);
        final String layout = layoutOf(dc);
        readingRanges = true;
        new Thread(() -> {
            final PathData stored = PathStore.load(name);
            // the table of contents is read only for a path with something left to read
            final Ranges found = needsRanges(stored) ? read(target) : null;
            post(() -> {
                readingRanges = false;
                if (data == null) {
                    // else a screen made a path meanwhile
                    data = stored;
                }
                if (found != null && ranges == null) {
                    ranges = found.byKey;
                    pageCount = found.pageCount;
                    rangesLayout = layout;
                }
            });
        }, "@T Path").start();
    }

    private static boolean needsRanges(PathData d) {
        return d != null && !d.isEmpty() && !d.allDone();
    }

    private void onPage(DocumentController dc) {
        final int[] shown = shown(dc);
        final int from = shown[0];
        final int to = shown[1];
        if (card.get() != null && dc.getCurentPageFirst1() != cardPage) {
            // a page turn takes the card away
            removeCard();
        }
        final String layout = layoutOf(dc);
        if (!readingRanges && needsRanges(data) && !layout.equals(rangesLayout)) {
            // never read, or the book was laid out anew (font, size): its chapters moved
            readingRanges = true;
            ranges = null;
            prevFrom = -1;
            prevLast = -1;
            final AiJobs.Target target = AiJobs.Target.of(dc);
            new Thread(() -> {
                final Ranges found = read(target);
                post(() -> {
                    readingRanges = false;
                    // even when unreadable: not asked again for this layout
                    rangesLayout = layout;
                    if (found != null) {
                        ranges = found.byKey;
                        pageCount = found.pageCount;
                    }
                });
            }, "@T Path").start();
            return;
        }
        if (from == prevFrom && to == prevLast) {
            return;
        }
        if (data != null && ranges != null && layout.equals(rangesLayout)) {
            final PathData.Chapter finished = data.finishedBy(ranges, prevFrom, prevLast, from, to, pageCount);
            if (finished != null) {
                finish(dc, finished, from, to);
            }
        }
        prevFrom = from;
        prevLast = to;
    }

    /** The reader turned out of a chapter of the path: it is read, and the next one is offered. */
    private void finish(DocumentController dc, PathData.Chapter finished, int from, int to) {
        final String key = finished.key;
        finished.done = true;
        PathStore.later(name, data.goal, d -> {
            final PathData.Chapter c = d.chapter(key);
            if (c != null) {
                c.done = true;
            }
        });
        final Activity a = owner();
        if (a == null) {
            return;
        }
        final PathData.Chapter next = data.nextAfter(key);
        if (next == null) {
            if (data.allDone()) {
                showCard(a, dc, null, null);
            }
            return;
        }
        final int[] r = ranges.get(next.key);
        if (r == null || (r[0] >= from && r[0] <= to)) {
            // the next chapter of the path is the one that starts here (or its place is not known)
            return;
        }
        showCard(a, dc, next, r);
    }

    // ------------------------------------------------------------------ the card

    /** "NEXT ON YOUR PATH · title · why · 16 min · Go there / Not now"; with {@code next} null, the path is done. */
    private void showCard(Activity a, DocumentController dc, PathData.Chapter next, int[] range) {
        final FrameLayout content = a.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        removeCard();
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(8), dp(8));
        box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), dp(16)));
        box.setElevation(dp(6));
        // taps on the card do not turn the page
        box.setClickable(true);

        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView label = new TextView(a);
        CanvasParts.label(label, true);
        label.setText(next == null ? R.string.canvas_t20_card_done_label : R.string.canvas_t20_card_next_label);
        texts.addView(label);
        final TextView title = new TextView(a);
        CanvasParts.text(title, CanvasLook.TEXT_BODY, 600);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(2), 0, 0);
        if (next == null) {
            title.setText(a.getString(R.string.canvas_t20_card_done, data.goal));
        } else {
            title.setText(name(a, next));
        }
        texts.addView(title);
        if (next != null) {
            final String time = PathText.duration(PathData.minutes(next.chars, ReadingSpeed.wpm(name)));
            final String note = next.why.isEmpty() ? time : time.isEmpty() ? next.why : next.why + " · " + time;
            if (!note.isEmpty()) {
                final TextView why = new TextView(a);
                CanvasParts.secondary(why);
                why.setText(note);
                texts.addView(why);
            }
        }
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView close = new TextView(a);
        CanvasParts.text(close, 16, 400);
        close.setTextColor(CanvasLook.muted());
        close.setGravity(Gravity.CENTER);
        close.setText("✕");
        close.setContentDescription(a.getString(R.string.close));
        close.setBackground(CanvasParts.ripple(null));
        close.setOnClickListener(v -> removeCard());
        top.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        box.addView(top);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        final TextView yes = new TextView(a);
        CanvasParts.text(yes, 14, 600);
        yes.setTextColor(CanvasLook.onAccent());
        yes.setGravity(Gravity.CENTER);
        yes.setPadding(dp(16), 0, dp(16), 0);
        yes.setBackground(CanvasParts.primaryButton());
        yes.setText(next == null ? R.string.canvas_t20_card_see_path : R.string.canvas_t20_card_go);
        yes.setOnClickListener(v -> {
            removeCard();
            if (next == null) {
                PathScreen.show(a, dc);
            } else {
                goTo(dc, range[0]);
            }
        });
        buttons.addView(yes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        final TextView no = new TextView(a);
        CanvasParts.text(no, 14, 600);
        no.setTextColor(CanvasLook.accent());
        no.setGravity(Gravity.CENTER);
        no.setPadding(dp(16), 0, dp(16), 0);
        no.setBackground(CanvasParts.ripple(null));
        no.setText(R.string.canvas_t20_card_not_now);
        no.setOnClickListener(v -> removeCard());
        final LinearLayout.LayoutParams noParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                CanvasLook.target());
        noParams.leftMargin = dp(8);
        buttons.addView(no, noParams);
        final LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(10);
        box.addView(buttons, buttonsParams);

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(Dips.screenWidth() - dp(24), dp(520)), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(24);
        content.addView(box, params);
        box.setAlpha(0);
        box.animate().alpha(1).setDuration(180).start();
        card = new WeakReference<>(box);
        cardPage = dc.getCurentPageFirst1();
    }

    /** "I.7 · Of the natural and market price", the title as the book gives it. */
    static String name(Activity a, PathData.Chapter c) {
        final String title = !c.title.isEmpty() ? c.title : !c.line.isEmpty() ? c.line
                : a.getString(R.string.canvas_t20_chapter);
        return c.code.isEmpty() ? title : c.code + " · " + title;
    }

    private void removeCard() {
        final View v = card.get();
        card = new WeakReference<>(null);
        if (v != null && v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

    private static int dp(int dp) {
        return CanvasLook.dp(dp);
    }
}
