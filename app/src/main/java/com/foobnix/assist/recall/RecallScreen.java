package com.foobnix.assist.recall;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.util.ArrayList;
import java.util.List;

/**
 * Blank-page recall of a chapter, full screen in the canvas look (Assist.RECALL):
 * <ul>
 * <li>the blank page (canvas "Recall: blank page"): a guess of how much will be remembered, the
 * reader's words typed or talked, a nudge, skip;</li>
 * <li>listening (canvas "Recall: listening"): the timer of about 30 seconds, the live transcript;</li>
 * <li>the results (canvas "Blank-page recall"): the words with Edit, each key idea remembered,
 * unsure or missed, and the guess against the score; a missed idea goes to its place in the book;
 * "A curious beginner asks" opens the teach-back.</li>
 * </ul>
 * The AI is asked once per tap on "Check my recall" ({@link RecallJob}); the attempt is stored as
 * {@code recall:<chapter key>} with a short history.
 */
public final class RecallScreen implements RecallJob.Screen, VoiceInput.Listener {

    private static final int BLANK = 0;
    private static final int LISTENING = 1;
    private static final int CHECKING = 2;
    private static final int RESULTS = 3;
    /** A press shorter than this keeps listening on until the next tap. */
    private static final long TAP_MS = 350;
    /** The canvas's "listening" dot. */
    private static final int LIVE_DOT = 0xFFF27420;

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final BookChapters.Range range;
    private final String chapterTitle;
    private final boolean dark = CanvasLook.isNight();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Dialog dialog;
    private LinearLayout page;
    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout micBox;
    private LinearLayout links;
    private LinearLayout bottom;
    private RecallViews.Mic mic;
    private TextView micLabel;
    private TextView micSub;
    private TextView startOver;
    private EditText box;
    private TextView checkButton;
    private RecallViews.Ring ring;
    private TextView clock;
    private TextView live;
    private RecallViews.Bars bars;

    private int state = BLANK;
    private boolean loaded;
    /** The chapter's stored item, or null. */
    private Recall.Attempt item;
    private int expected = 70;
    private String words = "";
    private boolean spoken;
    private int seconds;
    private String error;

    private VoiceInput voice;
    private boolean voiceOk;
    private boolean asked;
    private boolean latched;
    private boolean downStarted;
    private long downAt;
    private long talkStart;
    private boolean restarting;
    private String typedBefore = "";
    private String said = "";

    private RecallJob job;
    private boolean nudgeBusy;
    private boolean nudgeShown;

    private RecallScreen(Activity a, DocumentController dc, BookChapters.Range range) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.range = range;
        this.chapterTitle = range.title != null && !range.title.trim().isEmpty() ? range.title.trim()
                : a.getString(R.string.ai_pages, range.from + 1, range.to);
    }

    /** The recall of the chapter at the reader's page (the ✦ menu's "Blank-page recall"). */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.RECALL.isOn() || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final BookChapters.Range known = AiJobs.knownChapter(t);
        if (known != null) {
            show(a, dc, known);
            return;
        }
        new Thread(() -> {
            final BookChapters.Range found = AiJobs.chapterAt(t);
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> {
                    if (found == null || found.to <= found.from) {
                        Toast.makeText(a, R.string.ai_cannot_open, Toast.LENGTH_LONG).show();
                    } else {
                        show(a, dc, found);
                    }
                });
            }
        }, "@T Recall").start();
    }

    /** The recall of a chapter: its latest results when it has an attempt, else the blank page. */
    public static void show(Activity a, DocumentController dc, BookChapters.Range range) {
        if (!Assist.RECALL.isOn() || a == null || Apps.isDestroyedActivity(a) || dc == null
                || dc.getCurrentBook() == null || range == null) {
            return;
        }
        new RecallScreen(a, dc, range).open();
    }

    /** The reader said not now, or skipped: kept so the chapter is not offered again. Nothing is asked of the AI. */
    static void decline(final String bookName, final BookChapters.Range r) {
        new Thread(() -> {
            try {
                final Recall.Attempt before = RecallChapter.load(bookName, r.key);
                if (before == null || !before.isDone()) {
                    RecallChapter.save(bookName, r.key, Recall.declined(before, r.title));
                }
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T Recall").start();
    }

    // ------------------------------------------------------------------ the screen

    private void open() {
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());
        page.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CanvasLook.dp(CanvasLook.BAR_DP)));

        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        final LinearLayout inner = new LinearLayout(a);
        inner.setOrientation(LinearLayout.VERTICAL);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        inner.addView(content);
        micBox = micBox();
        inner.addView(micBox);
        links = new LinearLayout(a);
        links.setOrientation(LinearLayout.VERTICAL);
        links.setGravity(Gravity.CENTER_HORIZONTAL);
        links.setPadding(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), CanvasLook.dp(8));
        inner.addView(links);
        scroll.addView(inner);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(CanvasLook.dp(16), CanvasLook.dp(8), CanvasLook.dp(16), CanvasLook.dp(16));
        page.addView(bottom);

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> stop());

        voiceOk = VoiceInput.available(a);
        voice = new VoiceInput(a, this);
        // the answer to the microphone question shows when this screen has the focus again
        page.getViewTreeObserver().addOnWindowFocusChangeListener(has -> {
            if (has && asked) {
                micAnswered();
            }
        });
        render();
        dialog.show();

        final String book = target.name;
        final String key = range.key;
        new Thread(() -> {
            final Recall.Attempt stored = RecallChapter.load(book, key);
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    loaded = true;
                    item = stored;
                    if (stored != null && stored.isDone() && state == BLANK && words.trim().isEmpty()) {
                        state = RESULTS;
                    }
                    render();
                });
            }
        }, "@T Recall").start();
    }

    private void stop() {
        final RecallJob j = job;
        job = null;
        if (j != null) {
            j.cancel();
        }
        handler.removeCallbacksAndMessages(null);
        if (voice != null) {
            voice.destroy();
        }
    }

    private View topBar() {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView close = text("✕", 18, 400, CanvasLook.ink());
        close.setGravity(Gravity.CENTER);
        close.setBackground(CanvasParts.ripple(null));
        close.setContentDescription(a.getString(R.string.canvas_t17_close));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView title = text(target.title, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        final TextView sub = text(a.getString(R.string.canvas_t17_bar_sub, chapterTitle), CanvasLook.TEXT_SMALL, 400,
                CanvasLook.muted());
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sub.setGravity(Gravity.CENTER);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    /** The talk button with its lines; one view for the blank page and for listening, so a held finger stays on it. */
    private LinearLayout micBox() {
        final LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(CanvasLook.dp(16), 0, CanvasLook.dp(16), 0);
        mic = new RecallViews.Mic(a, 76, 26);
        mic.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // the page must not scroll the finger away from the button
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    v.setPressed(true);
                    if (state == LISTENING) {
                        downStarted = false;
                    } else {
                        downAt = SystemClock.uptimeMillis();
                        downStarted = startTalk();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (state != LISTENING) {
                        return true;
                    }
                    if (downStarted && SystemClock.uptimeMillis() - downAt < TAP_MS) {
                        // a tap: listening stays on until the next tap
                        latched = true;
                        micLines();
                    } else {
                        finishTalk();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (state == LISTENING && downStarted && !latched) {
                        finishTalk();
                    }
                    return true;
                default:
                    return true;
            }
        });
        // without touch (a screen reader): one activation starts, the next finishes
        mic.setOnClickListener(v -> {
            if (state == LISTENING) {
                finishTalk();
            } else if (startTalk()) {
                latched = true;
                micLines();
            }
        });
        b.addView(mic, new LinearLayout.LayoutParams(mic.side(), mic.side()));
        micLabel = text("", 14, 600, CanvasLook.ink());
        micLabel.setGravity(Gravity.CENTER);
        b.addView(micLabel);
        micSub = text(a.getString(R.string.canvas_t17_or_type), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        micSub.setGravity(Gravity.CENTER);
        micSub.setPadding(0, CanvasLook.dp(4), 0, 0);
        b.addView(micSub);
        startOver = link(a.getString(R.string.canvas_t17_start_over), 13, CanvasLook.ink2());
        startOver.setOnClickListener(v -> startOver());
        b.addView(startOver, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        return b;
    }

    private void micLines() {
        final boolean on = state == LISTENING;
        mic.setListening(on, on ? 84 : 76);
        mic.setContentDescription(a.getString(on ? R.string.canvas_t17_release : R.string.canvas_t17_hold));
        micLabel.setText(!on ? R.string.canvas_t17_hold : latched ? R.string.canvas_t17_tap_finish : R.string.canvas_t17_release);
        micSub.setVisibility(on ? View.GONE : View.VISIBLE);
        startOver.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ drawing a state

    private void render() {
        final boolean focused = box != null && box.hasFocus();
        content.removeAllViews();
        links.removeAllViews();
        bottom.removeAllViews();
        box = null;
        checkButton = null;
        ring = null;
        clock = null;
        live = null;
        bars = null;
        micBox.setVisibility(voiceOk && loaded && (state == BLANK || state == LISTENING) ? View.VISIBLE : View.GONE);
        micLines();
        if (!loaded) {
            return;
        }
        switch (state) {
            case LISTENING:
                listening();
                break;
            case CHECKING:
                checking();
                break;
            case RESULTS:
                results();
                break;
            default:
                blank(focused);
                break;
        }
        links.setVisibility(links.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        bottom.setVisibility(bottom.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void header(int sub) {
        final TextView label = new TextView(a);
        label.setText(R.string.canvas_t17_title);
        CanvasParts.label(label, true);
        label.setPadding(CanvasLook.dp(20), CanvasLook.dp(4), CanvasLook.dp(20), 0);
        content.addView(label);
        final TextView h = text(a.getString(R.string.canvas_t17_question), 22, 600, CanvasLook.ink());
        h.setPadding(CanvasLook.dp(20), CanvasLook.dp(4), CanvasLook.dp(20), 0);
        content.addView(h);
        final TextView s = text(a.getString(sub), 13, 400, CanvasLook.ink2());
        s.setPadding(CanvasLook.dp(20), CanvasLook.dp(4), CanvasLook.dp(20), 0);
        content.addView(s);
    }

    // --- the blank page ---

    private void blank(boolean focus) {
        header(voiceOk ? R.string.canvas_t17_blank_sub : R.string.canvas_t17_blank_sub_type);

        // first, a guess
        final LinearLayout guess = new LinearLayout(a);
        guess.setOrientation(LinearLayout.VERTICAL);
        guess.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(14)));
        guess.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(8));
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView ask = text(a.getString(R.string.canvas_t17_guess), 13, 600, CanvasLook.ink());
        row.addView(ask, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView percent = new TextView(a);
        percent.setTypeface(CanvasLook.mono(a, 500));
        percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        percent.setTextColor(CanvasLook.accent());
        percent.setText(a.getString(R.string.canvas_t17_percent, expected));
        percent.setPadding(CanvasLook.dp(8), 0, 0, 0);
        row.addView(percent);
        guess.addView(row);
        final SeekBar seek = new SeekBar(a);
        seek.setMax(10);
        seek.setProgress(Math.round(expected / 10f));
        seek.setProgressTintList(ColorStateList.valueOf(CanvasLook.accent()));
        seek.setThumbTintList(ColorStateList.valueOf(CanvasLook.accent()));
        seek.setContentDescription(a.getString(R.string.canvas_t17_guess));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                expected = progress * 10;
                percent.setText(a.getString(R.string.canvas_t17_percent, expected));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        guess.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.target()));
        content.addView(guess, margins(16, 16, 16, 0));

        // your words
        final TextView yours = text(a.getString(R.string.canvas_t17_your_words), 13, 600, CanvasLook.ink());
        yours.setPadding(CanvasLook.dp(20), CanvasLook.dp(14), CanvasLook.dp(20), CanvasLook.dp(6));
        content.addView(yours);
        box = new EditText(a);
        box.setText(words);
        box.setHint(R.string.canvas_t17_hint);
        CanvasParts.text(box, 16, 400);
        box.setTypeface(Typeface.SERIF);
        box.setHintTextColor(CanvasLook.muted());
        box.setLineSpacing(CanvasLook.dp(5), 1f);
        box.setGravity(Gravity.TOP | Gravity.START);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setMinHeight(CanvasLook.dp(voiceOk ? 160 : 220));
        box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.dp(14)));
        final int pad = CanvasLook.dp(14);
        box.setPadding(pad, pad, pad, pad);
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                words = s.toString();
                enableCheck();
            }
        });
        content.addView(box, margins(16, 0, 16, 0));
        if (focus) {
            box.requestFocus();
            box.setSelection(box.getText().length());
        }

        if (error != null) {
            final TextView e = text(error, 13, 400, CanvasLook.sensor());
            e.setPadding(CanvasLook.dp(20), CanvasLook.dp(10), CanvasLook.dp(20), 0);
            content.addView(e);
        }

        // the nudge: the first word of each idea
        final List<String> first = nudgeShown ? Recall.nudge(ideas()) : new ArrayList<String>();
        if (nudgeBusy || !first.isEmpty()) {
            final LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(CanvasParts.accentCard());
            card.setPadding(CanvasLook.dp(14), CanvasLook.dp(10), CanvasLook.dp(14), CanvasLook.dp(12));
            final TextView label = new TextView(a);
            label.setText(R.string.canvas_t17_nudge_label);
            CanvasParts.label(label, true);
            card.addView(label);
            final TextView t = text(nudgeBusy ? a.getString(R.string.canvas_t17_nudge_busy) : TextUtils.join("   ·   ", first),
                    15, nudgeBusy ? 400 : 600, CanvasLook.ink());
            t.setLineSpacing(CanvasLook.dp(4), 1f);
            t.setPadding(0, CanvasLook.dp(4), 0, 0);
            card.addView(t);
            content.addView(card, margins(16, 12, 16, 0));
        }

        if (!nudgeShown && !nudgeBusy) {
            final TextView nudge = link(a.getString(R.string.canvas_t17_nudge), 14, CanvasLook.accent());
            nudge.setOnClickListener(v -> nudge());
            links.addView(nudge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        }
        final TextView skip = link(a.getString(R.string.canvas_t17_skip), 13, CanvasLook.ink2());
        skip.setOnClickListener(v -> {
            if (item == null || !item.isDone()) {
                decline(target.name, range);
                RecallSession.done(target.path, range.key);
            }
            dialog.dismiss();
        });
        links.addView(skip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));

        checkButton = primary(a.getString(R.string.canvas_t17_check));
        checkButton.setOnClickListener(v -> check());
        bottom.addView(checkButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52)));
        enableCheck();
    }

    private void enableCheck() {
        if (checkButton != null) {
            final boolean on = !words.trim().isEmpty();
            checkButton.setEnabled(on);
            checkButton.setAlpha(on ? 1f : 0.4f);
        }
    }

    /** The ideas known without a request: the chapter's key passages, else the ones kept in its item. */
    private List<Recall.Idea> ideas() {
        return RecallChapter.ideas(target.name, range.key, item);
    }

    // --- listening ---

    private void listening() {
        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        top.setPadding(0, CanvasLook.dp(8), 0, 0);
        final View dot = new View(a);
        dot.setBackground(CanvasParts.round(LIVE_DOT, 0, CanvasLook.dp(4)));
        final LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(CanvasLook.dp(8), CanvasLook.dp(8));
        dp.rightMargin = CanvasLook.dp(6);
        top.addView(dot, dp);
        final TextView label = new TextView(a);
        label.setText(R.string.canvas_t17_listening);
        CanvasParts.label(label, true);
        top.addView(label);
        content.addView(top);
        final TextView h = text(a.getString(R.string.canvas_t17_keep_going), 22, 600, CanvasLook.ink());
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, CanvasLook.dp(4), 0, 0);
        content.addView(h);

        // the timer
        final FrameLayout timer = new FrameLayout(a);
        ring = new RecallViews.Ring(a);
        timer.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        final LinearLayout time = new LinearLayout(a);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.CENTER);
        clock = new TextView(a);
        clock.setTypeface(CanvasLook.mono(a, 500));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        clock.setTextColor(CanvasLook.ink());
        clock.setGravity(Gravity.CENTER);
        time.addView(clock);
        final TextView of = text(a.getString(R.string.canvas_t17_of_about, Recall.clock(Recall.ABOUT_SECONDS)),
                CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        of.setGravity(Gravity.CENTER);
        time.addView(of);
        timer.addView(time, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(CanvasLook.dp(132), CanvasLook.dp(132));
        tp.gravity = Gravity.CENTER_HORIZONTAL;
        tp.topMargin = CanvasLook.dp(18);
        content.addView(timer, tp);

        // the live transcript
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(16)));
        final int pad = CanvasLook.dp(14);
        card.setPadding(pad, pad, pad, pad);
        final TextView label2 = new TextView(a);
        label2.setText(R.string.canvas_t17_live);
        CanvasParts.label(label2, false);
        card.addView(label2);
        live = text(said, 16, 400, CanvasLook.ink());
        live.setTypeface(Typeface.SERIF);
        live.setLineSpacing(CanvasLook.dp(5), 1f);
        live.setMinLines(3);
        live.setPadding(0, CanvasLook.dp(8), 0, 0);
        card.addView(live);
        content.addView(card, margins(16, 22, 16, 0));

        bars = new RecallViews.Bars(a);
        final LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CanvasLook.dp(56));
        bp.topMargin = CanvasLook.dp(12);
        content.addView(bars, bp);
        tick();
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (state == LISTENING) {
                tick();
                handler.postDelayed(this, 250);
            }
        }
    };

    private void tick() {
        final int sec = (int) ((SystemClock.elapsedRealtime() - talkStart) / 1000);
        if (clock != null) {
            clock.setText(Recall.clock(sec));
        }
        if (ring != null) {
            ring.setProgress(sec / (float) Recall.ABOUT_SECONDS);
        }
    }

    // --- checking ---

    private void checking() {
        header(R.string.canvas_t17_results_sub);
        content.addView(wordsCard(false), margins(16, 14, 16, 0));
        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(CanvasLook.dp(20), CanvasLook.dp(18), CanvasLook.dp(20), 0);
        line.addView(new ProgressBar(a), new LinearLayout.LayoutParams(CanvasLook.dp(20), CanvasLook.dp(20)));
        final TextView t = text(a.getString(R.string.canvas_t17_checking), 14, 400, CanvasLook.ink2());
        t.setPadding(CanvasLook.dp(10), 0, 0, 0);
        line.addView(t);
        content.addView(line);

        final TextView cancel = link(a.getString(R.string.cancel), 14, CanvasLook.ink2());
        cancel.setOnClickListener(v -> {
            final RecallJob j = job;
            job = null;
            if (j != null) {
                j.cancel();
            }
            state = BLANK;
            render();
        });
        bottom.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.target()));
    }

    /** The reader's words in a card; with the results, "Record again" and "Edit" above them. */
    private View wordsCard(boolean actions) {
        final String shown = actions && item != null ? item.words : words.trim();
        final boolean talked = actions && item != null ? item.spoken : spoken;
        final int secs = actions && item != null ? item.seconds : seconds;
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(16)));
        final int pad = CanvasLook.dp(14);
        card.setPadding(pad, actions ? CanvasLook.dp(8) : pad, pad, pad);
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (actions && voiceOk) {
            final RecallViews.Mic again = new RecallViews.Mic(a, 44, 0);
            again.setContentDescription(a.getString(R.string.canvas_t17_record_again));
            again.setOnClickListener(v -> again(""));
            final LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(again.side(), again.side());
            ap.rightMargin = CanvasLook.dp(10);
            row.addView(again, ap);
        }
        final TextView how = new TextView(a);
        CanvasParts.label(how, false);
        how.setText(talked && secs > 0 ? a.getString(R.string.canvas_t17_transcribed, Recall.clock(secs))
                : a.getString(talked ? R.string.canvas_t17_spoken : R.string.canvas_t17_typed));
        row.addView(how, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (actions) {
            final TextView edit = link(a.getString(R.string.canvas_t17_edit), 14, CanvasLook.accent());
            edit.setOnClickListener(v -> again(shown));
            row.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        }
        card.addView(row);
        final TextView t = text(shown, 15, 400, CanvasLook.ink());
        t.setTypeface(Typeface.SERIF);
        t.setLineSpacing(CanvasLook.dp(5), 1f);
        t.setPadding(0, CanvasLook.dp(actions ? 4 : 8), 0, 0);
        card.addView(t);
        return card;
    }

    /** Back to the blank page for another try, starting from {@code text}. */
    private void again(String text) {
        words = text;
        spoken = !text.isEmpty() && item != null && item.spoken;
        seconds = text.isEmpty() || item == null ? 0 : item.seconds;
        if (item != null) {
            expected = item.expected;
        }
        error = null;
        state = BLANK;
        render();
        scroll.scrollTo(0, 0);
    }

    // --- the results ---

    private void results() {
        header(R.string.canvas_t17_results_sub);
        content.addView(wordsCard(true), margins(16, 14, 16, 0));

        final List<Recall.Idea> ideas = item.ideas;
        final int remembered = Recall.count(ideas, Recall.REMEMBERED);
        final int unsure = Recall.count(ideas, Recall.UNSURE);
        String headline = a.getResources().getQuantityString(R.plurals.canvas_t17_ideas_of, ideas.size(), remembered,
                ideas.size());
        if (unsure > 0) {
            headline += a.getString(R.string.canvas_t17_plus_half, unsure);
        }
        final TextView h = text(headline, 16, 600, CanvasLook.ink());
        h.setPadding(CanvasLook.dp(20), CanvasLook.dp(16), CanvasLook.dp(20), CanvasLook.dp(6));
        content.addView(h);

        boolean places = false;
        for (Recall.Idea idea : ideas) {
            final boolean jump = !Recall.REMEMBERED.equals(idea.state) && idea.hasPlace();
            places |= jump;
            content.addView(ideaRow(idea, jump));
        }

        // the guess against the score
        final LinearLayout check = new LinearLayout(a);
        check.setOrientation(LinearLayout.HORIZONTAL);
        check.setGravity(Gravity.CENTER_VERTICAL);
        final TextView label = new TextView(a);
        label.setText(R.string.canvas_t17_confidence);
        CanvasParts.label(label, false);
        check.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView numbers = text(a.getString(R.string.canvas_t17_expected_got, item.expected, item.got), 13, 400,
                CanvasLook.ink2());
        numbers.setPadding(CanvasLook.dp(8), 0, 0, 0);
        check.addView(numbers);
        content.addView(check, margins(20, 12, 20, 0));
        final RecallViews.Confidence bar = new RecallViews.Confidence(a, item.got, item.expected);
        bar.setContentDescription(a.getString(R.string.canvas_t17_expected_got, item.expected, item.got));
        final LinearLayout.LayoutParams barParams = margins(20, 6, 20, 0);
        barParams.height = CanvasLook.dp(14);
        content.addView(bar, barParams);

        if (item.history.size() > 1) {
            final List<String> earlier = new ArrayList<>();
            for (int i = Math.max(0, item.history.size() - 6); i < item.history.size() - 1; i++) {
                earlier.add(a.getString(R.string.canvas_t17_percent, item.history.get(i).got));
            }
            final TextView t = text(a.getString(R.string.canvas_t17_earlier, TextUtils.join(", ", earlier)),
                    CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            t.setPadding(CanvasLook.dp(20), CanvasLook.dp(8), CanvasLook.dp(20), 0);
            content.addView(t);
        }
        if (places) {
            final TextView t = text(a.getString(R.string.canvas_t17_tap_hint), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            t.setPadding(CanvasLook.dp(20), CanvasLook.dp(8), CanvasLook.dp(20), 0);
            content.addView(t);
        }

        content.addView(beginner(), margins(16, 16, 16, 8));

        final TextView finish = primary(a.getString(R.string.canvas_t17_finish));
        finish.setOnClickListener(v -> dialog.dismiss());
        bottom.addView(finish, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(52)));
    }

    /** "A curious beginner asks": the question the check wrote, and the door to teach-back (canvas "Teach-back"). */
    private View beginner() {
        final String question = item.question.trim().isEmpty() ? a.getString(R.string.canvas_t18_opener)
                : item.question.trim();
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView who = text("?", 15, 700, CanvasLook.accent());
        who.setGravity(Gravity.CENTER);
        who.setBackground(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(16)));
        who.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(CanvasLook.dp(32), CanvasLook.dp(32));
        wp.rightMargin = CanvasLook.dp(10);
        row.addView(who, wp);

        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        final float r = CanvasLook.dp(14);
        final float s = CanvasLook.dp(4);
        bg.setCornerRadii(new float[]{s, s, r, r, r, r, r, r});
        bg.setColor(CanvasLook.surface2());
        card.setBackground(bg);
        card.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(12));
        final TextView label = new TextView(a);
        label.setText(R.string.canvas_t18_beginner_asks);
        CanvasParts.label(label, false);
        card.addView(label);
        final TextView q = text(question, 14, 400, CanvasLook.ink());
        q.setLineSpacing(CanvasLook.dp(3), 1f);
        q.setPadding(0, CanvasLook.dp(8), 0, CanvasLook.dp(8));
        card.addView(q);
        final TextView explain = text(a.getString(R.string.canvas_t18_explain), 14, 600, CanvasLook.accent());
        explain.setGravity(Gravity.CENTER);
        explain.setPadding(CanvasLook.dp(14), 0, CanvasLook.dp(14), 0);
        explain.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.accentLine(), CanvasLook.radius()));
        explain.setOnClickListener(v -> {
            dialog.dismiss();
            com.foobnix.assist.teach.TeachScreen.show(a, dc, range, question);
        });
        card.addView(explain, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        row.addView(card, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /** One idea: its mark, its words, and "unsure" or "missed"; {@code jump}: a tap goes to its place in the book. */
    private View ideaRow(Recall.Idea idea, boolean jump) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.dp(jump ? CanvasLook.TARGET_DP : 32));
        row.setPadding(CanvasLook.dp(20), CanvasLook.dp(4), CanvasLook.dp(20), CanvasLook.dp(4));
        final RecallViews.Mark mark = new RecallViews.Mark(a, idea.state);
        final LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(CanvasLook.dp(20), CanvasLook.dp(20));
        mp.rightMargin = CanvasLook.dp(10);
        row.addView(mark, mp);
        final TextView t = text(idea.text, 14, 400, CanvasLook.ink());
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (Recall.UNSURE.equals(idea.state)) {
            final TextView tag = text(a.getString(R.string.canvas_t17_unsure), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            tag.setPadding(CanvasLook.dp(8), 0, 0, 0);
            row.addView(tag);
        } else if (Recall.MISSED.equals(idea.state)) {
            final TextView tag = text(a.getString(R.string.canvas_t17_missed), CanvasLook.TEXT_SMALL, 600, CanvasLook.sensor());
            tag.setPadding(CanvasLook.dp(8), 0, 0, 0);
            row.addView(tag);
        }
        row.setContentDescription(a.getString(Recall.REMEMBERED.equals(idea.state) ? R.string.canvas_t17_remembered
                : Recall.UNSURE.equals(idea.state) ? R.string.canvas_t17_unsure : R.string.canvas_t17_missed)
                + ": " + idea.text);
        if (jump) {
            row.setBackground(CanvasParts.ripple(null));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                // the results are stored: the ✦ menu's row brings them back
                AiJump.toQuote(dc, idea.quote().isEmpty() ? null : idea.quote(), idea.page(), range.from, range.to - 1);
            });
        }
        return row;
    }

    // ------------------------------------------------------------------ actions

    private void check() {
        if (words.trim().isEmpty() || state != BLANK) {
            return;
        }
        final RecallJob running = job;
        if (running != null) {
            // a nudge still on its way
            running.cancel();
            nudgeBusy = false;
        }
        hideKeyboard();
        error = null;
        final List<Recall.Idea> known = ideas();
        final boolean fromPassages = !RecallChapter.fromPassages(target.name, range.key).isEmpty();
        state = CHECKING;
        job = RecallJob.check(a, target, range, known, fromPassages, words.trim(), expected, spoken, seconds, this);
        job.start();
        render();
        scroll.scrollTo(0, 0);
    }

    private void nudge() {
        if (!ideas().isEmpty()) {
            nudgeShown = true;
            render();
            return;
        }
        if (job != null) {
            return;
        }
        // no ideas stored for this chapter: one request finds them, and the check then sends only them
        nudgeBusy = true;
        nudgeShown = true;
        error = null;
        job = RecallJob.ideas(a, target, range, this);
        job.start();
        render();
    }

    @Override
    public void onChecked(Recall.Attempt attempt) {
        job = null;
        item = attempt;
        state = RESULTS;
        RecallSession.done(target.path, range.key);
        render();
        scroll.scrollTo(0, 0);
    }

    @Override
    public void onIdeas(Recall.Attempt stored) {
        job = null;
        nudgeBusy = false;
        item = stored;
        if (state == BLANK) {
            render();
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        if (nudgeBusy) {
            nudgeBusy = false;
            nudgeShown = false;
        }
        error = message;
        if (state == CHECKING) {
            state = BLANK;
        }
        if (state == BLANK) {
            render();
        } else {
            Toast.makeText(a, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        nudgeBusy = false;
        nudgeShown = false;
        if (state == CHECKING) {
            state = BLANK;
        }
        if (state == BLANK) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }

    // ------------------------------------------------------------------ talking

    /** Begins listening; false when the microphone must be allowed first (the question is then asked). */
    private boolean startTalk() {
        if (!voiceOk || state != BLANK) {
            return false;
        }
        if (!VoiceInput.granted(a)) {
            asked = true;
            VoiceInput.ask(a);
            // refused for good, the system asks nothing and the focus never leaves
            handler.postDelayed(() -> {
                if (asked && page.hasWindowFocus()) {
                    micAnswered();
                }
            }, 1500);
            return false;
        }
        hideKeyboard();
        typedBefore = words.trim();
        said = "";
        latched = false;
        error = null;
        state = LISTENING;
        talkStart = SystemClock.elapsedRealtime();
        render();
        scroll.scrollTo(0, 0);
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 250);
        voice.start();
        return true;
    }

    private void finishTalk() {
        if (state == LISTENING) {
            micLabel.setText(R.string.canvas_t17_finishing);
            voice.finish();
        }
    }

    private void startOver() {
        if (state != LISTENING) {
            return;
        }
        restarting = true;
        voice.cancel();
        restarting = false;
        said = "";
        latched = true;
        talkStart = SystemClock.elapsedRealtime();
        if (live != null) {
            live.setText("");
        }
        if (bars != null) {
            bars.reset();
        }
        micLines();
        tick();
        voice.start();
    }

    private void micAnswered() {
        asked = false;
        if (!VoiceInput.granted(a)) {
            voiceOk = false;
            Toast.makeText(a, R.string.canvas_t17_voice_denied, Toast.LENGTH_LONG).show();
            if (state == BLANK) {
                render();
            }
        }
    }

    @Override
    public void onVoiceState(boolean on) {
        if (on || restarting || state != LISTENING) {
            return;
        }
        // the talk is over: its words join what was typed before
        handler.removeCallbacks(ticker);
        if (!said.trim().isEmpty()) {
            seconds += (int) ((SystemClock.elapsedRealtime() - talkStart) / 1000);
            spoken = true;
        }
        words = VoiceText.join(typedBefore, said);
        latched = false;
        state = BLANK;
        render();
    }

    @Override
    public void onVoiceText(String text) {
        said = text == null ? "" : text;
        if (live != null) {
            live.setText(said);
        }
    }

    @Override
    public void onVoiceLevel(float level) {
        if (bars != null) {
            bars.push(level);
        }
    }

    @Override
    public void onVoiceError(int code) {
        if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            voiceOk = false;
            Toast.makeText(a, R.string.canvas_t17_voice_denied, Toast.LENGTH_LONG).show();
            if (state == BLANK) {
                render();
            }
            return;
        }
        Toast.makeText(a, R.string.canvas_t17_voice_failed, Toast.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        try {
            final InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(page.getWindowToken(), 0);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    // ------------------------------------------------------------------ parts

    private static LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(CanvasLook.dp(left), CanvasLook.dp(top), CanvasLook.dp(right), CanvasLook.dp(bottom));
        return lp;
    }

    private TextView primary(String label) {
        final TextView b = text(label, 15, 600, CanvasLook.onAccent());
        b.setGravity(Gravity.CENTER);
        b.setBackground(CanvasParts.primaryButton());
        return b;
    }

    /** A plain text button, 44 dp tall. */
    private TextView link(String label, float sp, int color) {
        final TextView b = text(label, sp, 600, color);
        b.setGravity(Gravity.CENTER);
        b.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        b.setBackground(CanvasParts.ripple(null));
        return b;
    }

    private TextView text(String s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        CanvasParts.text(t, sp, weight);
        t.setTextColor(color);
        return t;
    }
}
