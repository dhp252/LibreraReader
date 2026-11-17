package com.foobnix.assist.readsyou;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.SideMatter;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.annotation.AnnotationSession;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.recall.RecallChapter;
import com.foobnix.assist.review.Review;
import com.foobnix.assist.zoom.ZoomData;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasStatusStrip;
import com.foobnix.eye.EyeMode;
import com.foobnix.eye.PresenceDetector;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;
import com.foobnix.stats.ReadingTracker;
import com.foobnix.stats.WordCounter;
import com.foobnix.tts.TTSEngine;
import com.foobnix.tts.TTSService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "The book reads you back" in the open book (Assist.READS_YOU), without the camera: what the
 * page turns say ({@link ReadsModel}), shown on the READING YOU strip, and three quiet offers:
 * "Say it another way?" after a re-read or a long page, "Time for a break?" after a long unbroken
 * session with a real slowdown, and the "What is stored" screen. {@link #pageShown} is its hook
 * in the reader and {@link #menu} its rows in the ✦ menu; both do nothing while the switch is off.
 * <p>
 * What is measured stays in the book's assist file as small counters ({@link ReadsStore}). The AI
 * is asked only from the help sheet, on a tap ({@link ReadsHelpSheet}).
 */
public final class ReadsYou {

    private static final long TICK_MS = 1000;
    /** A book may open on one page and go to its saved place right after (as Study's cards). */
    private static final long SETTLE_MS = 1200;
    /** The counters are written at most this often while reading, and when the book closes. */
    private static final long FLUSH_MS = 2 * 60_000L;
    /** The help chip leaves by itself after this long. */
    private static final long CHIP_MS = 30_000;
    /** Less reading than this, with no event, is not worth opening the book's file for. */
    private static final int WORTH_SECONDS = 20;
    /** "How you read it" is offered this long after the turn past a chapter's end, so another feature's card comes first. */
    private static final long MAP_CARD_MS = 1500;
    /** Room for the page's own footer line (time, page, battery) under the chip. */
    private static final int FOOTER_DP = 40;
    /** Room for the annotation toolbar when it is shown (as the other cards). */
    private static final int TOOLBAR_DP = 76;

    private static ReadsYou current;

    private final String path;
    private final String book;
    private WeakReference<DocumentController> dcRef;
    private WeakReference<Activity> ownerRef;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ReadsModel model = new ReadsModel(0);
    private ReadsState state = new ReadsState();
    private boolean stateLoaded;
    private boolean stateDirty;
    private boolean ready;
    private boolean ended;
    private String layout = "";
    private AiJobs.Target target;
    /** The chapters (or page blocks) in the reader's layout; null until read. */
    private List<BookChapters.Range> chapters;
    private boolean readingChapters;
    private final List<ReadsDelta> pending = new ArrayList<>();
    private long lastFlush;

    private CanvasStatusStrip strip;
    private String stripText = "";
    private int stripPercent = -1;
    private TextView chip;
    private int chipWhy;
    private ReadsCard card;
    private boolean breakCard;
    /** The chapters "How you read it" was offered for in this session. */
    private final Set<String> mapOffered = new HashSet<>();

    // with Eye mode's camera (T33): fed only while Eye mode runs; nothing here starts the camera
    private final ReadsEye eye = new ReadsEye();
    private final ReadsBlink blink = new ReadsBlink();
    private ReadsModel.Visit lastVisit;
    private long sessionSeen;
    /** The page (0-based) the last drift mark is on, and when the last drift card came up. */
    private int driftTag = -1;
    private long driftCardAt;
    private Object driftShown;
    /** The page help was last offered on. */
    private int offeredTag = -1;

    private final Runnable ticker = this::tick;
    private final Runnable chipGone = this::hideChip;

    private ReadsYou(DocumentController dc, String path) {
        this.path = path;
        this.book = AiHighlights.bookName(path);
        attach(dc);
    }

    // ------------------------------------------------------------------ the ways in

    /** The reader shows a page (DocumentController.saveCurrentPage, only while the switch is on). */
    public static void pageShown(DocumentController dc) {
        try {
            if (dc == null || dc.getCurrentBook() == null || dc.getPageCount() <= 0 || dc.getActivity() == null
                    || !Assist.READS_YOU.isOn()) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                dc.getActivity().runOnUiThread(() -> pageShown(dc));
                return;
            }
            final String path = dc.getCurrentBook().getPath();
            if (current == null || current.ended || !current.path.equals(path)) {
                if (current != null) {
                    current.end();
                }
                current = new ReadsYou(dc, path);
                current.begin();
            } else if (current.dc() != dc) {
                // the same book in a re-created reader: the strip and the cards lived in the old one
                current.removeViews();
                current.attach(dc);
            }
            current.onPage(dc);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** The ✦ menu's rows (AiMenu, only while the switch is on). */
    public static void menu(final Activity a, final DocumentController dc, MyPopupMenu menu) {
        if (!Assist.READS_YOU.isOn() || a == null || dc == null || menu == null || dc.getCurrentBook() == null) {
            return;
        }
        menu.getMenu(R.drawable.canvas_t31_ic_another_way, R.string.canvas_t31_menu_help, () -> {
            final ReadsYou s = of(dc);
            if (s != null) {
                s.openHelp(a, dc, ReadsHelpSheet.BY_HAND);
            } else {
                ReadsHelpSheet.show(a, dc, ReadsHelpSheet.BY_HAND, 0, null);
            }
        });
        menu.getMenu(R.drawable.canvas_t32_ic_map, R.string.canvas_t32_menu, () -> showMap(a, dc));
        menu.getMenu(R.drawable.canvas_t31_ic_stored, R.string.canvas_t31_stored_title, () -> showStored(a, dc));
    }

    /** "How you read it" for the chapter at the reader's page. */
    static void showMap(Activity a, DocumentController dc) {
        try {
            final ReadsYou s = of(dc);
            if (s != null) {
                // what the session measured so far is written first (the store's thread keeps the order)
                s.flush(true);
            }
            final int page = ReadingTracker.realPages(dc, dc.getCurentPageFirst1())[0] - 1;
            ReadsMapScreen.show(a, dc, Math.max(0, page), s == null ? 0 : s.model.pace());
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** "What is stored" for the open book. */
    static void showStored(Activity a, DocumentController dc) {
        final ReadsYou s = of(dc);
        ReadsStoredScreen.show(a, dc, s == null ? null : s.sessionLine(a), s == null ? null : s::forget);
    }

    /** The session of the book {@code dc} shows, or null. */
    static ReadsYou of(DocumentController dc) {
        final ReadsYou s = current;
        return s != null && !s.ended && dc != null && dc.getCurrentBook() != null
                && s.path.equals(dc.getCurrentBook().getPath()) ? s : null;
    }

    // ------------------------------------------------------------------ the session

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

    private void begin() {
        ReadsStore.IO.execute(() -> {
            final ReadsState stored = ReadsStore.state(book);
            handler.post(() -> {
                if (ended) {
                    return;
                }
                state = stored;
                stateLoaded = true;
                double pace = stored.pace;
                if (pace <= 0) {
                    // the reading statistics' speed for this book, until the book has its own here
                    final int wpm = ReadingSpeed.wpm(ExtUtils.getFileName(path));
                    pace = wpm > 0 ? 60_000.0 / wpm : 0;
                }
                model.stored(pace);
            });
        });
        handler.postDelayed(() -> {
            if (ended) {
                return;
            }
            ready = true;
            final DocumentController dc = dc();
            if (dc != null && owner() != null) {
                onPage(dc);
            }
        }, SETTLE_MS);
        handler.postDelayed(ticker, TICK_MS);
        EyeMode.setFrameWatcher(ReadsYou::eyeFrame);
    }

    /**
     * What Eye mode's camera says of a frame (EyeMode.FrameWatcher): only while Eye mode is on and
     * running in this reader, and only while the page is being read. Nothing of the frame is kept.
     */
    private static void eyeFrame(Activity owner, long t, PresenceDetector.State state, boolean closed, float gazeY) {
        try {
            final ReadsYou s = current;
            if (s == null || s.ended || !s.ready || Looper.myLooper() != Looper.getMainLooper()
                    || s.ownerRef.get() != owner || !Assist.READS_YOU.isOn()) {
                return;
            }
            final DocumentController dc = s.dc();
            if (dc == null || !isActive(owner, dc)) {
                return;
            }
            final int st = state == PresenceDetector.State.SEES ? ReadsEye.SEES
                    : state == PresenceDetector.State.AWAY ? ReadsEye.AWAY : ReadsEye.CANT_SEE;
            // a second pass needs a page that stands still: book mode only
            s.eye.frame(t, st, dc.isBookMode() ? gazeY : Float.NaN);
            s.blink.frame(t, st == ReadsEye.SEES, closed);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static String layoutOf(DocumentController dc) {
        final AiJobs.Target t = AiJobs.Target.of(dc);
        return dc.isTextFormat() ? t.width + "x" + t.height + "@" + t.fontSize : "fixed";
    }

    private static boolean isActive(Activity a, DocumentController dc) {
        return a.hasWindowFocus() && !dc.isVisibleDialog() && !TTSEngine.get().isPlaying()
                && !AppState.get().isAutoScroll;
    }

    /** Words on the view page, -1 while they are not counted (as the reading statistics count them). */
    private int words(DocumentController dc, int[] real) {
        int total = 0;
        for (int p = real[0]; p <= real[1]; p++) {
            final int w = WordCounter.words(path, p);
            if (w < 0) {
                return -1;
            }
            total += w;
        }
        return dc.isBookMode() && AppSP.get().isCut ? total / 2 : total;
    }

    private void onPage(DocumentController dc) {
        final Activity a = owner();
        if (a == null || ended) {
            return;
        }
        if (!ready) {
            updateStrip(a, dc);
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        final int view = dc.getCurentPageFirst1();
        final String l = layoutOf(dc);
        if (!l.equals(layout)) {
            if (!layout.isEmpty()) {
                // laid out anew: the pages before belong to the chapters as they were
                model.tick(now, isActive(a, dc));
                model.relaid(now);
                flush(true);
                chapters = null;
            }
            layout = l;
        }
        target = AiJobs.Target.of(dc);
        final boolean turned = view != model.page();
        final boolean first = model.page() < 0;
        final int[] real = ReadingTracker.realPages(dc, view);
        final int before = model.tag();
        model.tick(now, isActive(a, dc));
        model.page(now, view, real[0] - 1, words(dc, real));
        if (turned) {
            final ReadsEye.Page seen = eye.turn();
            if (!first) {
                lastVisit = null;
                collect();
                final ReadsModel.Visit v = lastVisit;
                if (seen.watchedMs > 0 || seen.passes > 0) {
                    final ReadsDelta d = new ReadsDelta(Math.max(0, before));
                    d.watched = (int) Math.round(seen.watchedMs / 1000.0);
                    d.away = (int) Math.round(seen.awayMs / 1000.0);
                    d.passes = seen.passes;
                    if (!d.isEmpty()) {
                        pending.add(d);
                    }
                }
                if (v != null && v.tag == before && view > v.page && view - v.page <= ReadsRules.TURN_MAX
                        && ReadsRules.isDriftFast(v.ms, v.words, model.pace(), seen.longestAwayMs)) {
                    // looked away, then passed the page far too fast
                    drift(v.tag, v.page, seen.longestAwayMs, v.ms);
                }
            }
            hideChip();
            if (card != null && card.isShown()) {
                // reading on is an answer too
                if (breakCard) {
                    snooze();
                }
                card.hide();
            }
            if (!first) {
                afterTurn(a, dc);
                offerMap(before, real[0] - 1);
            }
        }
        updateStrip(a, dc);
    }

    /** A page was turned: the finished stays go to the store, and the break card may come. */
    private void afterTurn(Activity a, DocumentController dc) {
        if (chapters == null) {
            readChapters();
        }
        flush(false);
        if (stateLoaded && ReadsRules.wantsBreak(model.sessionMs(), model.slowdown(), blink.more(),
                System.currentTimeMillis(), state.snoozeUntil) && !AppState.get().isEditMode
                && !otherCardShowing(a)) {
            showBreakCard(a, dc);
        }
    }

    /**
     * "How you read it", offered when the reader pages past the end of a chapter of the table of
     * contents that has page times stored: once per chapter in a session, a moment after the
     * turn, and not while another card shows.
     *
     * @param prev the book's page (0-based) shown before the turn
     * @param page the one shown now
     */
    private void offerMap(final int prev, final int page) {
        final BookChapters.Range r = ReadsStore.chapterAt(chapters, prev);
        if (r == null || prev < 0 || page < r.to || prev < r.to - 2 || page - prev > 4 || mapOffered.contains(r.key)
                || !Recall.offerable(r.key, r.title, SideMatter.isSideTitle(r.title), false)) {
            return;
        }
        mapOffered.add(r.key);
        flush(true);
        ReadsStore.IO.execute(() -> {
            final ReadsSignals stored = ReadsStore.signals(book, r.key);
            final int seconds = stored.total().seconds;
            final int spots = stored.spots();
            if (seconds >= ReadsRules.MAP_MIN_SECONDS) {
                handler.postDelayed(() -> showMapCard(r, seconds, spots, page), MAP_CARD_MS);
            }
        });
    }

    private void showMapCard(final BookChapters.Range r, int seconds, int spots, int page) {
        final Activity a = owner();
        final DocumentController dc = dc();
        if (ended || a == null || dc == null || model.tag() != page || AppState.get().isEditMode || dc.isVisibleDialog()
                || (card != null && card.isShown()) || otherCardShowing(a)) {
            return;
        }
        hideChip();
        if (card == null) {
            card = new ReadsCard(a);
        }
        final ReadsCard v = card;
        breakCard = false;
        final int minutes = Math.max(1, ReadsRules.minutes(seconds * 1000L));
        v.clear();
        v.label(a.getString(R.string.canvas_t32_card_label));
        v.title(a.getString(R.string.canvas_t32_title));
        v.line(spots > 0 ? a.getString(R.string.canvas_t32_card_line_spots, r.title.trim(), minutes, spots)
                : a.getString(R.string.canvas_t32_card_line, r.title.trim(), minutes));
        v.button(a.getString(R.string.canvas_t32_card_open), null, true, () -> {
            v.hide();
            ReadsMapScreen.show(a, dc, r.from, model.pace());
        });
        v.link(a.getString(R.string.canvas_t32_card_later), false, v::hide);
        v.show(AnnotationSession.isShownFor(dc));
    }

    private void tick() {
        if (ended) {
            return;
        }
        final Activity a = owner();
        final DocumentController dc = dc();
        if (current != this || a == null || dc == null || a.isFinishing() || !Assist.READS_YOU.isOn()) {
            end();
            return;
        }
        try {
            if (ready) {
                final long now = SystemClock.elapsedRealtime();
                model.tick(now, isActive(a, dc));
                if (!model.wordsKnown() && model.page() == dc.getCurentPageFirst1()) {
                    model.words(words(dc, ReadingTracker.realPages(dc, model.page())));
                }
                final ReadsModel.Help help = model.help(now);
                if (help != ReadsModel.Help.NONE) {
                    offerHelp(a, dc, help == ReadsModel.Help.REREAD ? ReadsHelpSheet.REREAD : ReadsHelpSheet.DWELL);
                }
                if (model.sessionMs() < sessionSeen) {
                    // the unbroken session started again: so does the blink baseline
                    blink.reset();
                }
                sessionSeen = model.sessionMs();
                final long back = eye.returned();
                if (ReadsRules.isDriftAway(back) && isActive(a, dc)) {
                    // the eyes were off this page for a long stretch and are back
                    drift(model.tag(), model.page(), back, 0);
                }
                if (eye.passed() && offeredTag != model.tag()) {
                    offerHelp(a, dc, ReadsHelpSheet.PASS);
                }
                updateStrip(a, dc);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        handler.postDelayed(ticker, TICK_MS);
    }

    /** The book closes, another one opens, or the switch went off: what was measured is written, the views go. */
    private void end() {
        if (ended) {
            return;
        }
        ended = true;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(chipGone);
        if (current == this) {
            EyeMode.setFrameWatcher(null);
        }
        try {
            final ReadsEye.Page seen = eye.turn();
            if (seen.watchedMs > 0 || seen.passes > 0) {
                final ReadsDelta d = new ReadsDelta(Math.max(0, model.tag()));
                d.watched = (int) Math.round(seen.watchedMs / 1000.0);
                d.away = (int) Math.round(seen.awayMs / 1000.0);
                d.passes = seen.passes;
                if (!d.isEmpty() && model.tag() >= 0) {
                    pending.add(d);
                }
            }
            model.end(SystemClock.elapsedRealtime());
            flush(true);
        } catch (Exception e) {
            LOG.e(e);
        }
        removeViews();
        if (current == this) {
            current = null;
        }
    }

    // ------------------------------------------------------------------ the store

    private void collect() {
        for (ReadsModel.Visit v : model.drain()) {
            lastVisit = v;
            final ReadsDelta d = ReadsDelta.of(v);
            if (!d.isEmpty()) {
                pending.add(d);
            }
        }
    }

    /** One event on the page shown (help offered or taken, the break card). */
    private ReadsDelta mark() {
        final ReadsDelta d = new ReadsDelta(Math.max(0, model.tag()));
        pending.add(d);
        return d;
    }

    /**
     * Writes what was measured since the last time, off the main thread; while reading at most
     * every {@link #FLUSH_MS}. The chapters are read first when this layout's are not known yet.
     */
    private void flush(boolean now) {
        collect();
        if (model.paceLearned() && Math.abs(model.pace() - state.pace) >= 1) {
            state.pace = model.pace();
            state.paceSamples = model.paceSamples();
            stateDirty = true;
        }
        final long time = SystemClock.elapsedRealtime();
        if (!now && time - lastFlush < FLUSH_MS) {
            return;
        }
        int seconds = 0;
        boolean event = false;
        for (ReadsDelta d : pending) {
            seconds += d.seconds;
            event |= d.rereads + d.longs + d.offers + d.helps + d.breaks + d.drifts + d.passes > 0;
        }
        final boolean worth = event || seconds >= WORTH_SECONDS;
        if (!worth && !(stateDirty && stateLoaded)) {
            return;
        }
        lastFlush = time;
        final List<ReadsDelta> batch = worth ? new ArrayList<>(pending) : Collections.<ReadsDelta>emptyList();
        if (worth) {
            pending.clear();
        }
        final ReadsState save = stateDirty && stateLoaded ? state.copy() : null;
        stateDirty = false;
        final List<BookChapters.Range> known = chapters;
        final AiJobs.Target t = target;
        final String l = layout;
        ReadsStore.IO.execute(() -> {
            if (!batch.isEmpty()) {
                final List<BookChapters.Range> list = known != null ? known : chaptersOf(t);
                if (list != null) {
                    ReadsStore.add(book, list, batch);
                    if (known == null) {
                        handler.post(() -> gotChapters(list, l));
                    }
                }
            }
            if (save != null) {
                ReadsStore.saveState(book, save);
            }
        });
    }

    /** The chapters from the table of contents, or page blocks without one, in a layout. Off the main thread. */
    private static List<BookChapters.Range> chaptersOf(AiJobs.Target t) {
        if (t == null) {
            return null;
        }
        try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
            return text == null ? null
                    : BookChapters.chaptersOrBlocks(AiJobs.toc(t, text), text.pageCount(), BookChapters.BLOCK_PAGES);
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    private void readChapters() {
        if (readingChapters || target == null) {
            return;
        }
        readingChapters = true;
        final AiJobs.Target t = target;
        final String l = layout;
        ReadsStore.IO.execute(() -> {
            final List<BookChapters.Range> list = chaptersOf(t);
            handler.post(() -> {
                readingChapters = false;
                if (list != null) {
                    gotChapters(list, l);
                }
            });
        });
    }

    private void gotChapters(List<BookChapters.Range> list, String forLayout) {
        if (!ended && chapters == null && forLayout.equals(layout)) {
            chapters = list;
        }
    }

    /** "Delete these signals" was done: the session forgets what it has not written yet, and its stored pace. */
    private void forget() {
        model.drain();
        pending.clear();
        state = new ReadsState();
        stateDirty = false;
    }

    private void snooze() {
        state.snoozeUntil = System.currentTimeMillis() + ReadsRules.SNOOZE_MS;
        stateDirty = true;
        flush(true);
    }

    // ------------------------------------------------------------------ the strip

    /**
     * The READING YOU strip: the last row of the reader's bottom bar, so it shows and hides with
     * the bars and covers neither the page's own footer line nor a card.
     */
    private void updateStrip(Activity a, DocumentController dc) {
        if (strip == null) {
            final View anchor = a.findViewById(R.id.playListParent);
            if (anchor == null || !(anchor.getParent() instanceof LinearLayout)
                    || ((LinearLayout) anchor.getParent()).getOrientation() != LinearLayout.VERTICAL) {
                return;
            }
            strip = new CanvasStatusStrip(a);
            // the reader's own tinting of its bars leaves the strip's colours alone
            strip.setTag("no_tint");
            strip.setOnClickListener(v -> showStored(a, dc));
            strip.setOnLongClickListener(v -> {
                showStored(a, dc);
                return true;
            });
            ((LinearLayout) anchor.getParent()).addView(strip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.STRIP_DP)));
            stripText = "";
            stripPercent = -1;
        }
        final String text = a.getString(R.string.canvas_t31_strip, a.getString(stateWord()));
        if (!text.equals(stripText)) {
            stripText = text;
            strip.setStatus(text);
        }
        final boolean camera = eye.live(SystemClock.uptimeMillis());
        if (camera != strip.isSensor()) {
            // the eye in the sensor colour only while Eye mode's camera results arrive
            strip.setSensor(camera);
        }
        final int pages = dc.getPageCount();
        final int percent = pages > 0 ? Math.round(100f * dc.getCurentPageFirst1() / pages) : 0;
        if (percent != stripPercent) {
            stripPercent = percent;
            strip.setProgress(percent / 100f);
        }
    }

    private int stateWord() {
        if (!ready) {
            return R.string.canvas_t31_state_learning;
        }
        if (eye.away(SystemClock.uptimeMillis())) {
            return R.string.canvas_t33_state_away;
        }
        if (eye.passes() > 0) {
            return R.string.canvas_t33_state_pass;
        }
        switch (model.state(SystemClock.elapsedRealtime())) {
            case REREAD:
                return R.string.canvas_t31_state_reread;
            case LONG_PAGE:
                return R.string.canvas_t31_state_long;
            case SLOWING:
                return R.string.canvas_t31_state_slowing;
            case STEADY:
                return R.string.canvas_t31_state_steady;
            default:
                return R.string.canvas_t31_state_learning;
        }
    }

    /** What this session measured so far, for the "What is stored" screen (kept in memory only). */
    private String sessionLine(Activity a) {
        final int wpm = ReadsRules.wpm(model.pace());
        final String pace = wpm > 0 ? a.getString(R.string.canvas_t31_session_pace, wpm)
                : a.getString(R.string.canvas_t31_session_no_pace);
        final int slower = ReadsRules.percent(model.slowdown());
        final String line = a.getString(R.string.canvas_t31_session, ReadsRules.minutes(model.sessionMs()), pace);
        final String all = slower > 0 ? line + " · " + a.getString(R.string.canvas_t31_chip_slower, slower) : line;
        return blink.watchedMs() > 0 ? all + "\n" + a.getString(R.string.canvas_t33_session_camera,
                ReadsUi.clock(blink.watchedMs()), blink.baseline(), blink.recent()) : all;
    }

    // ------------------------------------------------------------------ "Say it another way?"

    /**
     * Whether a card of another feature (Study's, an offer) is on the reader now: a visible,
     * raised, vertical box laid over the page that is not this feature's own (the check of
     * {@code RecallCard.otherCardShowing}).
     */
    private boolean otherCardShowing(Activity a) {
        final FrameLayout content = (FrameLayout) a.findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        for (int i = 1; i < content.getChildCount(); i++) {
            final View v = content.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE || (card != null && v == card.view()) || !(v instanceof LinearLayout)) {
                continue;
            }
            if (((LinearLayout) v).getOrientation() == LinearLayout.VERTICAL && v.getElevation() > 0 && v.isClickable()) {
                return true;
            }
        }
        return false;
    }

    /** The quiet chip: once per page, never while a card shows or the bars are over the page. */
    private void offerHelp(Activity a, DocumentController dc, int why) {
        if ((chip != null && chip.getVisibility() == View.VISIBLE) || AppState.get().isEditMode || dc.isVisibleDialog()
                || (card != null && card.isShown()) || otherCardShowing(a)) {
            // asked again at the next tick, while the page still calls for it
            return;
        }
        model.helpOffered();
        offeredTag = model.tag();
        mark().offers = 1;
        chipWhy = why;
        if (chip == null) {
            chip = ReadsUi.text(a, a.getString(R.string.canvas_t31_chip), 13, 600, CanvasLook.accent());
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
            chip.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.accentLine(), CanvasLook.dp(20))));
            chip.setElevation(CanvasLook.dp(3));
            chip.setVisibility(View.GONE);
            chip.setOnClickListener(v -> {
                final DocumentController now = dc();
                final Activity owner = owner();
                hideChip();
                if (now != null && owner != null) {
                    openHelp(owner, now, chipWhy);
                }
            });
            final FrameLayout content = a.findViewById(android.R.id.content);
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    CanvasLook.dp(40), Gravity.BOTTOM | Gravity.END);
            lp.rightMargin = CanvasLook.dp(12);
            content.addView(chip, lp);
        }
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) chip.getLayoutParams();
        lp.bottomMargin = CanvasLook.dp(FOOTER_DP + (AnnotationSession.isShownFor(dc) ? TOOLBAR_DP : 0));
        chip.setLayoutParams(lp);
        chip.animate().cancel();
        chip.setAlpha(0);
        chip.setVisibility(View.VISIBLE);
        chip.bringToFront();
        chip.animate().alpha(1).setDuration(180).start();
        handler.removeCallbacks(chipGone);
        handler.postDelayed(chipGone, CHIP_MS);
    }

    private void hideChip() {
        handler.removeCallbacks(chipGone);
        if (chip != null && chip.getVisibility() == View.VISIBLE) {
            chip.animate().cancel();
            chip.setVisibility(View.GONE);
        }
    }

    /** The help sheet for the page shown; an AI request on it makes the page a spot to review. */
    private void openHelp(Activity a, DocumentController dc, int why) {
        model.helpOffered();
        final ReadsDelta spot = new ReadsDelta(Math.max(0, model.tag()));
        ReadsHelpSheet.show(a, dc, why, model.pageMs(), () -> {
            if (ended) {
                return;
            }
            spot.helps = 1;
            pending.add(spot);
            flush(true);
        });
    }

    // ------------------------------------------------------------------ "Time for a break?"

    private void showBreakCard(final Activity a, final DocumentController dc) {
        if (card == null) {
            card = new ReadsCard(a);
        }
        final ReadsCard v = card;
        breakCard = true;
        state.breaksOffered++;
        stateDirty = true;
        mark().breaks = 1;
        final int minutes = ReadsRules.minutes(model.sessionMs());
        final int slower = ReadsRules.percent(model.slowdown());
        v.clear();
        v.label(a.getString(R.string.canvas_t31_break_card_label));
        v.title(a.getString(R.string.canvas_t31_break_card_title));
        final boolean slowing = ReadsRules.isSlowdown(model.slowdown());
        v.line(a.getString(slowing ? R.string.canvas_t31_break_card_line : R.string.canvas_t33_break_line_blink, minutes));
        final List<String> chips = new ArrayList<>();
        if (slowing) {
            chips.add(a.getString(R.string.canvas_t31_chip_slower, slower));
        }
        if (blink.more()) {
            // only with Eye mode's camera, against this session's own first minutes
            chips.add(a.getString(R.string.canvas_t33_chip_blinking));
        }
        v.chips(chips);
        v.button(a.getString(R.string.canvas_t31_take_five), null, true, () -> {
            v.hide();
            state.breaksTaken++;
            stateDirty = true;
            flush(true);
            ReadsBreak.show(a, () -> {
                // the break is a pause: the session starts again
                model.breakTaken();
                blink.reset();
            });
        });
        if (canListen(dc)) {
            v.button(a.getString(R.string.canvas_t31_listen), a.getString(R.string.canvas_t31_listen_sub), false, () -> {
                v.hide();
                state.listens++;
                snooze();
                // the app's own read-aloud, from the page shown
                TTSService.playPause(a, dc);
            });
        }
        v.link(a.getString(R.string.canvas_t31_keep_reading), false, () -> {
            v.hide();
            snooze();
        });
        if (Assist.RECALL.isOn()) {
            v.link(a.getString(R.string.canvas_t31_before_you_go), true, () -> {
                v.hide();
                snooze();
                com.foobnix.assist.recall.RecallScreen.showHere(a, dc);
            });
        }
        v.onClose(this::snooze);
        v.show(AnnotationSession.isShownFor(dc));
        flush(true);
    }

    // ------------------------------------------------------------------ "You drifted on this page" (T33)

    /**
     * A drift on a page, from Eye mode's camera: the page gets a drift mark, and a card comes up
     * with what is stored for that page (a key passage's idea or the zoom section's line; the AI
     * is not asked). The card is skipped when one was up a moment ago or something else is shown.
     *
     * @param tag      the book's page, 0-based
     * @param viewPage the reader's page number "Reread it" goes to
     * @param awayMs   how long the eyes were off the page in one stretch
     * @param passedMs how fast the page was then passed; 0 when the reader is still on it
     */
    private void drift(final int tag, final int viewPage, final long awayMs, final long passedMs) {
        if (tag < 0 || tag == driftTag) {
            return;
        }
        driftTag = tag;
        final ReadsDelta d = new ReadsDelta(tag);
        d.drifts = 1;
        pending.add(d);
        flush(true);
        final long now = SystemClock.elapsedRealtime();
        if (driftCardAt > 0 && now - driftCardAt < ReadsRules.DRIFT_QUIET_MS) {
            return;
        }
        final List<BookChapters.Range> known = chapters;
        final AiJobs.Target t = target;
        ReadsStore.IO.execute(() -> {
            Recall.Idea idea = null;
            BookChapters.Range in = null;
            try {
                in = ReadsStore.chapterAt(known != null ? known : chaptersOf(t), tag);
                if (in != null) {
                    final ZoomData zoom = ZoomData.fromJson(AssistStore.get().item(book, ZoomData.key(in.key)));
                    idea = ReadsSpots.ideaAt(tag - in.from, in.pages(), in.from, RecallChapter.fromPassages(book, in.key), zoom);
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final Recall.Idea said = idea;
            final BookChapters.Range chapter = in;
            handler.post(() -> showDriftCard(tag, viewPage, awayMs, passedMs, chapter, said));
        });
    }

    private void showDriftCard(int tag, final int viewPage, long awayMs, long passedMs, BookChapters.Range chapter,
                               Recall.Idea said) {
        final Activity a = owner();
        final DocumentController dc = dc();
        if (ended || a == null || dc == null || AppState.get().isEditMode || dc.isVisibleDialog()
                || (passedMs == 0 && model.tag() != tag) || (card != null && card.isShown()) || otherCardShowing(a)) {
            return;
        }
        hideChip();
        if (card == null) {
            card = new ReadsCard(a);
        }
        final ReadsCard v = card;
        breakCard = false;
        driftCardAt = SystemClock.elapsedRealtime();
        final Object shown = new Object();
        driftShown = shown;
        v.clear();
        v.label(a.getString(R.string.canvas_t33_drift_label));
        v.title(a.getString(passedMs > 0 ? R.string.canvas_t33_drift_title_last : R.string.canvas_t33_drift_title));
        v.line(passedMs > 0 ? a.getString(R.string.canvas_t33_drift_line_fast, ReadsUi.clock(awayMs), ReadsUi.clock(passedMs))
                : a.getString(R.string.canvas_t33_drift_line, ReadsUi.clock(awayMs)));
        if (said != null) {
            // only what is already stored for that page; nothing is asked of the AI
            v.said(a.getString(R.string.canvas_t33_it_said), said.text);
        }
        v.button(a.getString(R.string.canvas_t33_reread), null, true, () -> {
            v.hide();
            dc.onGoToPage(viewPage);
        });
        v.link(a.getString(R.string.canvas_t33_got_it), false, v::hide);
        v.link(a.getString(R.string.canvas_t33_stays_link), false, () -> showStored(a, dc));
        v.onClose(() -> driftShown = null);
        v.show(AnnotationSession.isShownFor(dc));
        if (said != null && chapter != null) {
            // the spot's idea goes to the review queue as "How you read it" adds them (only with Spaced review on)
            final String title = chapter.title != null && !chapter.title.trim().isEmpty() ? chapter.title.trim()
                    : a.getString(R.string.ai_pages, chapter.from + 1, chapter.to);
            Review.addSpots(a, dc, chapter.key, title, Collections.singletonList(said), (added, already) -> {
                if (driftShown == shown && card == v && v.isShown()) {
                    v.note(a.getString(added > 0 ? R.string.canvas_t33_saved : R.string.canvas_t33_saved_already));
                }
            });
        }
    }

    /** Read-aloud can start from this page: one page at a time in view, text on it, and not playing already. */
    private boolean canListen(DocumentController dc) {
        try {
            if (TTSEngine.get().isPlaying() || (dc.isBookMode() && (AppSP.get().isDouble || AppSP.get().isCut))) {
                return false;
            }
            return words(dc, ReadingTracker.realPages(dc, dc.getCurentPageFirst1())) > 0;
        } catch (Exception e) {
            LOG.e(e);
            return false;
        }
    }

    private void removeViews() {
        hideChip();
        remove(chip);
        chip = null;
        remove(strip);
        strip = null;
        if (card != null) {
            card.remove();
            card = null;
        }
    }

    private static void remove(View v) {
        if (v != null && v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }
}
