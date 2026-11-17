package com.foobnix.assist.teach;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.BookChapters;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.recall.Recall;
import com.foobnix.assist.recall.RecallCard;
import com.foobnix.assist.recall.RecallChapter;
import com.foobnix.assist.recall.RecallJob;
import com.foobnix.assist.recall.RecallViews;
import com.foobnix.assist.recall.VoiceInput;
import com.foobnix.assist.recall.VoiceText;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import org.json.JSONObject;

import java.util.List;

/**
 * Teach-back, full screen in the canvas look (canvas "Teach-back", Assist.RECALL): a chat in
 * which a curious beginner asks about the chapter and the reader explains, typing or talking.
 * Each answer is one request ({@link TeachJob}); a gap the model flags is shown as "GAP FOUND"
 * only when its quote is in the book's passages ({@link Teach#verify}), and a tap on it goes to
 * the quote (the chat waits behind a small card). The header counts ideas explained and gaps;
 * after {@link Teach#MAX_TURNS} answers, or when every idea is explained, the beginner closes and
 * a summary shows. Stored as {@code teach:<chapter key>} is that summary, not the chat.
 */
public final class TeachScreen implements TeachJob.Screen, VoiceInput.Listener {

    /** The canvas's gap orange, its fills by day and by night. */
    private static final int GAP_LINE = 0xFFF27420;
    private static final int GAP_FILL = 0xFFFFF8F3;
    private static final int GAP_FILL_N = 0xFF2B2018;
    private static final int GAP_CHIP = 0xFFFFF1E6;

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    private final BookChapters.Range range;
    private final String chapterTitle;
    private final boolean dark = CanvasLook.isNight();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Dialog dialog;
    private LinearLayout page;
    private LinearLayout chips;
    private ScrollView scroll;
    private LinearLayout chat;
    private LinearLayout bottom;
    private EditText box;
    private RecallViews.Mic mic;
    private TextView send;

    private Teach.Session session;
    private AiJobs.Job job;
    private boolean busy;
    private String status;
    private String error;
    private String draft = "";
    private boolean draftSpoken;
    private boolean saved;
    private RecallCard paused;

    private VoiceInput voice;
    private boolean voiceOk;
    private boolean asked;
    private String typedBefore = "";

    private TeachScreen(Activity a, DocumentController dc, BookChapters.Range range) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
        this.range = range;
        this.chapterTitle = range.title != null && !range.title.trim().isEmpty() ? range.title.trim()
                : a.getString(R.string.ai_pages, range.from + 1, range.to);
    }

    /** A teach-back on the chapter at the reader's page (the ✦ menu's "Teach-back"). */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.RECALL.isOn() || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final BookChapters.Range known = AiJobs.knownChapter(t);
        if (known != null) {
            show(a, dc, known, null);
            return;
        }
        new Thread(() -> {
            final BookChapters.Range found = AiJobs.chapterAt(t);
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> {
                    if (found == null || found.to <= found.from) {
                        Toast.makeText(a, R.string.ai_cannot_open, Toast.LENGTH_LONG).show();
                    } else {
                        show(a, dc, found, null);
                    }
                });
            }
        }, "@T Teach").start();
    }

    /**
     * A teach-back on a chapter.
     *
     * @param question the beginner's first question (from the recall's results), or null
     */
    public static void show(Activity a, DocumentController dc, BookChapters.Range range, String question) {
        if (!Assist.RECALL.isOn() || a == null || Apps.isDestroyedActivity(a) || dc == null
                || dc.getCurrentBook() == null || range == null) {
            return;
        }
        new TeachScreen(a, dc, range).open(question);
    }

    // ------------------------------------------------------------------ the screen

    private void open(final String question) {
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());
        page.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CanvasLook.dp(CanvasLook.BAR_DP)));

        chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(CanvasLook.dp(16), CanvasLook.dp(4), CanvasLook.dp(16), CanvasLook.dp(10));
        page.addView(chips);
        final View rule = new View(a);
        rule.setBackgroundColor(CanvasLook.surface2());
        page.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));

        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        chat = new LinearLayout(a);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setGravity(Gravity.BOTTOM);
        chat.setPadding(CanvasLook.dp(16), CanvasLook.dp(12), CanvasLook.dp(16), CanvasLook.dp(12));
        scroll.addView(chat);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final View rule2 = new View(a);
        rule2.setBackgroundColor(CanvasLook.line());
        page.addView(rule2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));
        bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(16));
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
        status = a.getString(R.string.canvas_t18_opening);
        render();
        dialog.show();

        final String book = target.name;
        final String key = range.key;
        new Thread(() -> {
            final Recall.Attempt stored = RecallChapter.load(book, key);
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> {
                    if (dialog.isShowing()) {
                        loaded(stored, question);
                    }
                });
            }
        }, "@T Teach").start();
    }

    /** The chapter's ideas: the stored ones, else one request finds them (and its first question). */
    private void loaded(Recall.Attempt stored, String question) {
        final List<Recall.Idea> ideas = RecallChapter.ideas(target.name, range.key, stored);
        final String first = question != null && !question.trim().isEmpty() ? question
                : stored != null ? stored.question : "";
        if (!ideas.isEmpty()) {
            begin(ideas, first);
            return;
        }
        status = a.getString(R.string.canvas_t18_reading);
        render();
        job = RecallJob.ideas(a, target, range, new RecallJob.Screen() {
            @Override
            public void onChecked(Recall.Attempt attempt) {
            }

            @Override
            public void onIdeas(Recall.Attempt item) {
                job = null;
                final List<Recall.Idea> found = RecallChapter.ideas(target.name, range.key, item);
                if (found.isEmpty()) {
                    onError(a.getString(R.string.canvas_t18_unreadable));
                    return;
                }
                begin(found, item.question);
            }

            @Override
            public void onError(String message) {
                job = null;
                Toast.makeText(a, message, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            }

            @Override
            public void onNeedSettings() {
                job = null;
                dialog.dismiss();
                AiSettingsDialog.show(a, null);
            }
        });
        job.start();
    }

    private void begin(List<Recall.Idea> ideas, String question) {
        status = null;
        session = new Teach.Session(ideas, question == null || question.trim().isEmpty()
                ? a.getString(R.string.canvas_t18_opener) : question.trim());
        render();
    }

    private void stop() {
        final AiJobs.Job j = job;
        job = null;
        if (j != null) {
            j.cancel();
        }
        handler.removeCallbacksAndMessages(null);
        if (voice != null) {
            voice.destroy();
        }
        if (paused != null) {
            paused.remove();
            paused = null;
        }
        save();
    }

    /** Keeps the session's summary once it has an answer; again when it changes. */
    private void save() {
        if (session == null || session.answers == 0 || saved) {
            return;
        }
        saved = true;
        final String book = target.name;
        final String key = Teach.key(range.key);
        final String json;
        try {
            json = session.summary(range.title, System.currentTimeMillis()).toString();
        } catch (Exception e) {
            LOG.e(e);
            return;
        }
        new Thread(() -> {
            try {
                AssistStore.get().put(book, key, new JSONObject(json));
            } catch (Exception e) {
                LOG.e(e);
            }
        }, "@T Teach").start();
    }

    private View topBar() {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text("‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setBackground(CanvasParts.ripple(null));
        back.setContentDescription(a.getString(R.string.canvas_t18_back));
        back.setOnClickListener(v -> dialog.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView title = text(a.getString(R.string.canvas_t18_title), CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        final TextView sub = text(a.getString(R.string.canvas_t18_sub, chapterTitle), CanvasLook.TEXT_SMALL, 400,
                CanvasLook.muted());
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sub.setGravity(Gravity.CENTER);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        chips();
        chat.removeAllViews();
        if (session != null) {
            for (Teach.Line line : session.lines) {
                if (line.kind == Teach.Line.READER) {
                    chat.addView(readerBubble(line));
                } else if (line.kind == Teach.Line.GAP) {
                    chat.addView(gapCard(line.gap), gap(12));
                } else {
                    chat.addView(beginnerBubble(line.text), gap(12));
                }
            }
            if (session.finished) {
                chat.addView(summary(), gap(16));
            }
        }
        if (busy || status != null) {
            chat.addView(beginnerBubble(status != null ? status : "…"), gap(12));
        }
        if (error != null) {
            final TextView retry = text(a.getString(R.string.canvas_t18_retry, error), 13, 600, CanvasLook.accent());
            retry.setBackground(CanvasParts.ripple(null));
            retry.setMinHeight(CanvasLook.target());
            retry.setGravity(Gravity.CENTER_VERTICAL);
            retry.setOnClickListener(v -> ask());
            chat.addView(retry, gap(8));
        }
        input();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    /** "3 of 5 ideas explained" with a bar per idea, and "1 gap found". */
    private void chips() {
        chips.removeAllViews();
        if (session == null) {
            chips.setVisibility(View.GONE);
            return;
        }
        chips.setVisibility(View.VISIBLE);
        final LinearLayout done = new LinearLayout(a);
        done.setOrientation(LinearLayout.HORIZONTAL);
        done.setGravity(Gravity.CENTER_VERTICAL);
        done.setBackground(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(10)));
        done.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        final int total = session.ideas.size();
        for (int i = 0; i < total; i++) {
            final View seg = new View(a);
            seg.setBackground(CanvasParts.round(session.isExplained(i) ? CanvasLook.accent() : CanvasLook.accentLine(), 0,
                    CanvasLook.dp(2)));
            final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(CanvasLook.dp(6), CanvasLook.dp(12));
            sp.rightMargin = CanvasLook.dp(2);
            done.addView(seg, sp);
        }
        final TextView count = text(a.getResources().getQuantityString(R.plurals.canvas_t18_explained, total,
                session.explainedCount(), total), 12, 600, CanvasLook.accent());
        count.setSingleLine(true);
        count.setEllipsize(TextUtils.TruncateAt.END);
        count.setPadding(CanvasLook.dp(6), 0, 0, 0);
        done.addView(count);
        chips.addView(done, new LinearLayout.LayoutParams(0, CanvasLook.dp(36), 1));
        final int gaps = session.gaps.size();
        if (gaps > 0) {
            final TextView g = text("⚠ " + a.getResources().getQuantityString(R.plurals.canvas_t18_gaps, gaps, gaps), 12, 600,
                    CanvasLook.sensor());
            g.setGravity(Gravity.CENTER_VERTICAL);
            g.setBackground(CanvasParts.round(dark ? CanvasLook.sensorTint() : GAP_CHIP, 0, CanvasLook.dp(10)));
            g.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
            final LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    CanvasLook.dp(36));
            gp.leftMargin = CanvasLook.dp(8);
            chips.addView(g, gp);
        }
    }

    private static LinearLayout.LayoutParams gap(int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        return lp;
    }

    /** A bubble: all corners 16 dp but one of 4 dp (bottom left for the beginner, bottom right for the reader). */
    private static GradientDrawable bubble(int fill, boolean reader) {
        final GradientDrawable d = new GradientDrawable();
        final float r = CanvasLook.dp(16);
        final float s = CanvasLook.dp(4);
        d.setCornerRadii(reader ? new float[]{r, r, r, r, s, s, r, r} : new float[]{r, r, r, r, r, r, s, s});
        d.setColor(fill);
        return d;
    }

    private View beginnerBubble(String words) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(0, 0, CanvasLook.dp(40), 0);
        final TextView who = text("?", 13, 700, CanvasLook.accent());
        who.setGravity(Gravity.CENTER);
        who.setBackground(CanvasParts.round(CanvasLook.accentTint(), 0, CanvasLook.dp(14)));
        who.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(CanvasLook.dp(28), CanvasLook.dp(28));
        wp.rightMargin = CanvasLook.dp(8);
        row.addView(who, wp);
        final TextView t = text(words, 14, 400, CanvasLook.ink());
        t.setLineSpacing(CanvasLook.dp(3), 1f);
        t.setBackground(bubble(CanvasLook.surface2(), false));
        t.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        t.setContentDescription(a.getString(R.string.canvas_t18_beginner_says, words));
        row.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View readerBubble(Teach.Line line) {
        final LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.END);
        col.setPadding(CanvasLook.dp(56), CanvasLook.dp(12), 0, 0);
        final TextView t = text(line.text, 14, 400, CanvasLook.onAccent());
        t.setLineSpacing(CanvasLook.dp(3), 1f);
        t.setBackground(bubble(CanvasLook.accent(), true));
        t.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (line.spoken) {
            final TextView tag = text(a.getString(R.string.canvas_t18_spoken), 11, 400, CanvasLook.muted());
            tag.setPadding(0, CanvasLook.dp(4), 0, 0);
            col.addView(tag, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return col;
    }

    /** "GAP FOUND": what the book says, its own words, and the way to them. */
    private View gapCard(Teach.Gap gap) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final GradientDrawable bg = CanvasParts.round(dark ? GAP_FILL_N : GAP_FILL, 0, CanvasLook.dp(14));
        bg.setStroke(Math.max(2, Math.round(1.5f * a.getResources().getDisplayMetrics().density)), GAP_LINE,
                CanvasLook.dp(5), CanvasLook.dp(4));
        card.setBackground(bg);
        card.setPadding(CanvasLook.dp(12), CanvasLook.dp(12), CanvasLook.dp(12), CanvasLook.dp(6));
        final TextView label = new TextView(a);
        label.setText("⚠ " + a.getString(R.string.canvas_t18_gap_found));
        CanvasParts.label(label, false);
        label.setTextColor(CanvasLook.sensor());
        card.addView(label);
        final TextView say = text(gap.say, 14, 400, CanvasLook.ink());
        say.setLineSpacing(CanvasLook.dp(3), 1f);
        say.setPadding(0, CanvasLook.dp(6), 0, 0);
        card.addView(say);
        final TextView quote = text("“" + gap.quote + "”", 14, 400, CanvasLook.ink2());
        quote.setTypeface(Typeface.SERIF, Typeface.ITALIC);
        quote.setLineSpacing(CanvasLook.dp(3), 1f);
        quote.setPadding(0, CanvasLook.dp(6), 0, 0);
        card.addView(quote);
        final TextView see = text(gap.page >= 0 ? a.getString(R.string.canvas_t18_see_page, gap.page + 1)
                : a.getString(R.string.canvas_t18_see_book), 13, 600, CanvasLook.accent());
        see.setGravity(Gravity.CENTER_VERTICAL);
        see.setBackground(CanvasParts.ripple(null));
        see.setOnClickListener(v -> look(gap.quote, gap.page));
        card.addView(see, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CanvasLook.target()));
        return card;
    }

    /** The closing summary: each idea explained or not yet; one not explained goes to its place in the book. */
    private View summary() {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.card());
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(12), CanvasLook.dp(14), CanvasLook.dp(8));
        final TextView label = new TextView(a);
        label.setText(R.string.canvas_t18_done_label);
        CanvasParts.label(label, true);
        card.addView(label);
        final int total = session.ideas.size();
        String line = a.getResources().getQuantityString(R.plurals.canvas_t18_explained, total, session.explainedCount(), total);
        if (!session.gaps.isEmpty()) {
            line += " · " + a.getResources().getQuantityString(R.plurals.canvas_t18_gaps, session.gaps.size(),
                    session.gaps.size());
        }
        final TextView h = text(line, 16, 600, CanvasLook.ink());
        h.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(6));
        card.addView(h);
        for (int i = 0; i < total; i++) {
            final Recall.Idea idea = session.ideas.get(i);
            final boolean yes = session.isExplained(i);
            final LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(CanvasLook.dp(!yes && idea.hasPlace() ? CanvasLook.TARGET_DP : 32));
            final RecallViews.Mark mark = new RecallViews.Mark(a, yes ? Recall.REMEMBERED : Recall.MISSED);
            final LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(CanvasLook.dp(20), CanvasLook.dp(20));
            mp.rightMargin = CanvasLook.dp(10);
            row.addView(mark, mp);
            row.addView(text(idea.text, 14, 400, CanvasLook.ink()),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (!yes) {
                final TextView tag = text(a.getString(R.string.canvas_t18_not_yet), CanvasLook.TEXT_SMALL, 600, CanvasLook.sensor());
                tag.setPadding(CanvasLook.dp(8), 0, 0, 0);
                row.addView(tag);
                if (idea.hasPlace()) {
                    row.setBackground(CanvasParts.ripple(null));
                    row.setOnClickListener(v -> look(idea.quote(), idea.page()));
                }
            }
            card.addView(row);
        }
        return card;
    }

    /** The input row: the box, and one round button that talks, stops or sends; "Done" when the chat is over. */
    private void input() {
        bottom.removeAllViews();
        box = null;
        mic = null;
        send = null;
        if (session == null) {
            bottom.setVisibility(View.GONE);
            return;
        }
        bottom.setVisibility(View.VISIBLE);
        if (session.finished) {
            final TextView done = text(a.getString(R.string.canvas_t18_done), 15, 600, CanvasLook.onAccent());
            done.setGravity(Gravity.CENTER);
            done.setBackground(CanvasParts.primaryButton());
            done.setOnClickListener(v -> dialog.dismiss());
            bottom.addView(done, new LinearLayout.LayoutParams(0, CanvasLook.dp(52), 1));
            return;
        }
        box = new EditText(a);
        box.setText(draft);
        box.setHint(R.string.canvas_t18_hint);
        CanvasParts.text(box, 14, 400);
        box.setHintTextColor(CanvasLook.muted());
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setMaxLines(4);
        box.setMinHeight(CanvasLook.target());
        box.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.dp(22)));
        box.setPadding(CanvasLook.dp(14), CanvasLook.dp(8), CanvasLook.dp(14), CanvasLook.dp(8));
        box.setEnabled(!busy && error == null);
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                draft = s.toString();
                button();
            }
        });
        bottom.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final FrameLayout round = new FrameLayout(a);
        mic = new RecallViews.Mic(a, 44, 0);
        mic.setContentDescription(a.getString(R.string.canvas_t18_talk));
        mic.setOnClickListener(v -> talk());
        round.addView(mic, new FrameLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        send = text("↑", 20, 700, CanvasLook.onAccent());
        send.setGravity(Gravity.CENTER);
        send.setBackground(CanvasParts.round(CanvasLook.accent(), 0, CanvasLook.dp(22)));
        send.setOnClickListener(v -> {
            if (voice.isListening()) {
                voice.finish();
            } else {
                send();
            }
        });
        round.addView(send, new FrameLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        final LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target());
        rp.leftMargin = CanvasLook.dp(8);
        bottom.addView(round, rp);
        button();
    }

    /** The round button: stop while listening, send with words in the box, else talk (or a dim send without voice). */
    private void button() {
        if (send == null || mic == null) {
            return;
        }
        final boolean listening = voice.isListening();
        final boolean words = !draft.trim().isEmpty();
        final boolean free = !busy && error == null;
        final boolean showMic = !listening && !words && voiceOk;
        mic.setVisibility(showMic ? View.VISIBLE : View.GONE);
        mic.setEnabled(free);
        mic.setAlpha(free ? 1f : 0.4f);
        send.setVisibility(showMic ? View.GONE : View.VISIBLE);
        send.setText(listening ? "■" : "↑");
        send.setTextSize(listening ? 14 : 20);
        send.setContentDescription(a.getString(listening ? R.string.canvas_t18_stop : R.string.canvas_t18_send));
        final boolean on = listening || (words && free);
        send.setEnabled(on);
        send.setAlpha(on ? 1f : 0.4f);
    }

    // ------------------------------------------------------------------ the turns

    private void send() {
        final String words = draft.trim();
        if (session == null || session.finished || busy || error != null || words.isEmpty()) {
            return;
        }
        session.answer(words, draftSpoken);
        draft = "";
        draftSpoken = false;
        saved = false;
        hideKeyboard();
        ask();
    }

    /** One request for the reader's last answer (again after an error). */
    private void ask() {
        if (session == null || job != null) {
            return;
        }
        error = null;
        busy = true;
        final TeachJob j = new TeachJob(a, target, Teach.user(target.title, chapterTitle, session), session.isLast(), this);
        job = j;
        j.start();
        render();
    }

    @Override
    public void onReply(Teach.Reply reply) {
        job = null;
        busy = false;
        session.apply(reply);
        saved = false;
        if (session.finished) {
            save();
        }
        render();
    }

    @Override
    public void onError(String message) {
        job = null;
        busy = false;
        error = message;
        render();
    }

    @Override
    public void onNeedSettings() {
        job = null;
        busy = false;
        error = a.getString(R.string.canvas_t18_need_key);
        render();
        AiSettingsDialog.show(a, null);
    }

    /** Goes to a place in the book; the chat waits behind a small card. */
    private void look(String quote, int page) {
        hideKeyboard();
        dialog.hide();
        AiJump.toQuote(dc, quote == null || quote.isEmpty() ? null : quote, page, range.from, range.to - 1);
        if (paused == null) {
            paused = new RecallCard(a);
        }
        final RecallCard card = paused;
        card.clear();
        card.label(a.getString(R.string.canvas_t18_title));
        card.title(a.getString(R.string.canvas_t18_paused));
        card.button(a.getString(R.string.canvas_t18_resume), true, () -> {
            card.hide();
            if (!Apps.isDestroyedActivity(a)) {
                dialog.show();
            }
        });
        card.onClose(() -> dialog.dismiss());
        card.show(false);
    }

    // ------------------------------------------------------------------ talking

    private void talk() {
        if (!voiceOk || busy || error != null || session == null || session.finished) {
            return;
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
            return;
        }
        typedBefore = draft.trim();
        if (box != null) {
            box.setHint(R.string.canvas_t18_listening);
        }
        voice.start();
        button();
    }

    private void micAnswered() {
        asked = false;
        if (!VoiceInput.granted(a)) {
            voiceOk = false;
            Toast.makeText(a, R.string.canvas_t17_voice_denied, Toast.LENGTH_LONG).show();
            button();
        }
    }

    @Override
    public void onVoiceState(boolean listening) {
        if (!listening && box != null) {
            box.setHint(R.string.canvas_t18_hint);
        }
        button();
    }

    @Override
    public void onVoiceText(String text) {
        if (box == null || text == null || text.trim().isEmpty()) {
            return;
        }
        draftSpoken = true;
        box.setText(VoiceText.join(typedBefore, text));
        box.setSelection(box.getText().length());
    }

    @Override
    public void onVoiceLevel(float level) {
    }

    @Override
    public void onVoiceError(int code) {
        if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            voiceOk = false;
            Toast.makeText(a, R.string.canvas_t17_voice_denied, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(a, R.string.canvas_t17_voice_failed, Toast.LENGTH_LONG).show();
        }
        button();
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

    private TextView text(String s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        CanvasParts.text(t, sp, weight);
        t.setTextColor(color);
        return t;
    }
}
