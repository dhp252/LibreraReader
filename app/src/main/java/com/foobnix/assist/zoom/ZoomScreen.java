package com.foobnix.assist.zoom;

import android.app.Activity;
import android.app.Dialog;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiMenu;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiSettings;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasCard;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.canvas.CanvasSegmented;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.stats.ReadingSpeed;

/**
 * Semantic zoom of the chapter at the reader's page (canvas ZoomFull, ZoomLean, ZoomClaims,
 * ZoomLine): a full-screen reading view with the zoom bar Full / Lean / Claims / 1 line. Full is
 * the book itself, so it closes the view. The other levels come from one AI request for the
 * chapter ({@link ZoomJob}), sent the first time the reader picks one and kept with the book's
 * assist data; later they open from the device. A claim, a one-line row or a folded part goes to
 * its place in the book. Opened from the ✦ menu, only when Assist.ZOOM is on.
 * <p>
 * Extras (canvas ZoomExpand, ZoomGoal): a tap on a Lean sentence shows the author's own passage
 * under it, found again in the chapter's text; before a book's first request the reader says why
 * they read it ({@link ZoomGoalScreen}), and that goal goes with every request of the book; with
 * Assist.XRAY on the bar ends in an X-ray button.
 */
public final class ZoomScreen implements ZoomJob.Screen {

    private static final int FULL = 0;
    private static final int LEAN = 1;
    private static final int CLAIMS = 2;
    private static final int LINE = 3;

    private final Activity a;
    private final DocumentController dc;
    private final AiJobs.Target target;
    /** The chapter at the reader's page; null until the book was asked. */
    private BookChapters.Range range;
    /** The chapter was looked up and its stored levels read. */
    private boolean ready;
    private ZoomData data;
    private int level = FULL;
    private ZoomJob job;
    private int received;
    private String error;
    /** Why the reader reads this book, or null before they said; {@code bookGoal} is the key passages' goal. */
    private ZoomGoal goal;
    private String bookGoal = "";
    /** The Lean sentence whose original is open, or null. */
    private ZoomData.Sentence opened;
    /** The chapter's text in this layout, read when the first original is asked for; null before. */
    private ZoomText chapterText;
    private boolean readingText;

    private Dialog dialog;
    private TextView subtitle;
    private CanvasSegmented bar;
    private TextView status;
    private TextView goalLink;
    private ScrollView scroll;
    private LinearLayout body;
    /** The line under "Condensing this chapter…" while a request runs. */
    private TextView busyNote;

    private ZoomScreen(Activity a, DocumentController dc) {
        this.a = a;
        this.dc = dc;
        this.target = AiJobs.Target.of(dc);
    }

    /** The zoom view of the chapter at the reader's page (the ✦ menu's "Semantic zoom"). */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.ZOOM.isOn() || a == null || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        new ZoomScreen(a, dc).open();
    }

    private void open() {
        final boolean dark = CanvasLook.isNight();
        dialog = new Dialog(a, dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CanvasLook.bg());

        page.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, CanvasLook.dp(CanvasLook.BAR_DP)));
        page.addView(zoomBar());

        scroll = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(12), gutter, CanvasLook.dp(24));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
        render();

        // the chapter and what is stored for it: read off the main thread, nothing is sent
        new Thread(() -> {
            final BookChapters.Range found = AiJobs.chapterAt(target);
            final ZoomData stored = found == null ? null : ZoomStore.load(target.name, found.key);
            final ZoomGoal storedGoal = ZoomStore.loadGoal(target.name);
            final String keyPassagesGoal = ZoomStore.bookGoal(target.name);
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                if (!dialog.isShowing()) {
                    return;
                }
                range = found;
                data = stored;
                goal = storedGoal;
                bookGoal = keyPassagesGoal;
                ready = true;
                if (found == null) {
                    error = a.getString(R.string.ai_cannot_open);
                } else if (stored != null) {
                    level = LEAN;
                }
                render();
            });
        }, "@T Zoom").start();
    }

    // ------------------------------------------------------------------ bars

    private View topBar() {
        final LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = ZoomLook.text(a, "‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(a.getString(R.string.zoom_back));
        back.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        back.setOnClickListener(v -> dialog.dismiss());
        top.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView title = ZoomLook.text(a, target.title, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(title);
        subtitle = ZoomLook.text(a, a.getString(R.string.zoom_title), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return top;
    }

    /** The zoom bar: the segmented control and the status line under it. */
    private View zoomBar() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        box.setPadding(gutter, CanvasLook.dp(4), gutter, 0);

        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        bar = CanvasSegmented.create(a, a.getString(R.string.zoom_full), a.getString(R.string.zoom_lean),
                a.getString(R.string.zoom_claims), a.getString(R.string.zoom_line));
        bar.getView().setContentDescription(a.getString(R.string.zoom_title));
        bar.setOnSelectListener(this::pick);
        row.addView(bar.getView(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (Assist.XRAY.isOn()) {
            // The Argument X-ray is another feature on another branch: until the two are joined this
            // button only goes back to the book, where the X-ray's marks and its ✦ row are. To wire:
            // call com.foobnix.assist.xray.XrayScreen.showHere(a, dc) after the dismiss.
            final TextView xray = ZoomLook.text(a, a.getString(R.string.zoom_xray), 13, 600, CanvasLook.ink2());
            xray.setGravity(Gravity.CENTER);
            xray.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
            xray.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(13))));
            xray.setOnClickListener(v -> dialog.dismiss());
            final LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            xlp.leftMargin = CanvasLook.dp(8);
            row.addView(xray, xlp);
        }
        box.addView(row);

        status = ZoomLook.text(a, "", CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        status.setPadding(0, CanvasLook.dp(8), 0, 0);
        box.addView(status);

        // "Change it any time from the zoom bar": the book's reading goal
        goalLink = ZoomLook.text(a, "", CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        goalLink.setGravity(Gravity.CENTER_VERTICAL);
        goalLink.setMinHeight(CanvasLook.dp(32));
        goalLink.setVisibility(View.GONE);
        goalLink.setOnClickListener(v -> askGoal(-1));
        box.addView(goalLink, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /**
     * The goal screen. "Start reading" keeps the goal with the book; before the chapter's first
     * request it then sends that request for the level the reader picked.
     *
     * @param wanted the level picked that needs the request, or -1 when opened from the zoom bar
     */
    private void askGoal(int wanted) {
        if (!ready || range == null || job != null) {
            return;
        }
        final int wpm = ReadingSpeed.wpm(target.name);
        ZoomGoalScreen.show(a, target.title, goal, bookGoal, data, data == null ? 0 : ZoomTime.minutes(data.chars, wpm),
                data == null ? 0 : ZoomTime.minutes(data.leanChars(), wpm), chosen -> {
                    goal = chosen;
                    new Thread(() -> {
                        try {
                            ZoomStore.saveGoal(target.name, chosen);
                        } catch (Exception e) {
                            LOG.e(e);
                        }
                    }, "@T Zoom goal").start();
                    if (!dialog.isShowing()) {
                        return;
                    }
                    if (data == null && wanted > FULL) {
                        level = wanted;
                        request();
                    } else {
                        render();
                    }
                });
    }

    /** The reader picked a level of the bar. */
    private void pick(int index) {
        if (index == FULL) {
            // Full is the book itself: back to it
            dialog.dismiss();
            return;
        }
        if (!ready || range == null) {
            bar.select(level);
            return;
        }
        if (data == null && job == null && goal == null) {
            // the book's first request: why the reader reads it comes first
            bar.select(level);
            askGoal(index);
            return;
        }
        level = index;
        error = null;
        opened = null;
        if (data == null && job == null) {
            request();
            return;
        }
        render();
        scroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------------ the request

    /** Asks the AI for the chapter's levels: only ever from a tap. */
    private void request() {
        if (job != null || range == null || !Assist.ZOOM.isOn()) {
            return;
        }
        error = null;
        received = 0;
        opened = null;
        job = new ZoomJob(a, target, range, goal, bookGoal, this);
        render();
        job.start();
    }

    private void stop() {
        if (job != null) {
            job.cancel();
            job = null;
        }
    }

    @Override
    public void onProgress(int chars) {
        received = chars;
        if (job != null && busyNote != null) {
            busyNote.setText(a.getString(R.string.zoom_received, received));
        }
    }

    @Override
    public void onDone(ZoomData made) {
        job = null;
        data = made;
        if (level == FULL) {
            level = LEAN;
        }
        if (dialog.isShowing()) {
            render();
            scroll.scrollTo(0, 0);
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        error = message;
        if (data == null) {
            level = FULL;
        }
        if (dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (data == null) {
            level = FULL;
        }
        if (dialog.isShowing()) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }

    // ------------------------------------------------------------------ drawing

    private void render() {
        bar.select(level);
        if (range != null) {
            subtitle.setText(TextUtils.isEmpty(range.title) ? a.getString(R.string.ai_pages, range.from + 1, range.to) : range.title);
        }
        status.setText(statusText());
        goalLink.setVisibility(ready && range != null ? View.VISIBLE : View.GONE);
        goalLink.setText(goal == null ? a.getString(R.string.zoom_goal_bar_none)
                : a.getString(R.string.zoom_goal_bar, a.getString(ZoomGoalScreen.title(goal.kind))));
        body.removeAllViews();
        busyNote = null;
        if (error != null) {
            final TextView e = ZoomLook.text(a, error, CanvasLook.TEXT_SMALL, 500, CanvasLook.sensor());
            e.setPadding(0, 0, 0, CanvasLook.dp(12));
            body.addView(e);
        }
        if (!ready) {
            busy(a.getString(R.string.zoom_finding), null);
        } else if (job != null) {
            busyNote = busy(a.getString(R.string.zoom_working), received > 0 ? a.getString(R.string.zoom_received, received)
                    : a.getString(R.string.zoom_working_note));
        } else if (data == null || level == FULL) {
            intro();
        } else {
            if (goal != null && !data.goal.equals(goal.id(bookGoal))) {
                // condensed for another goal (or before one was set): made again only on a tap
                final LinearLayout stale = new LinearLayout(a);
                stale.setOrientation(LinearLayout.HORIZONTAL);
                stale.setGravity(Gravity.CENTER_VERTICAL);
                stale.addView(ZoomLook.text(a, a.getString(R.string.zoom_goal_stale), CanvasLook.TEXT_SMALL, 400, CanvasLook.ink2()),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                final TextView again = ZoomLook.text(a, a.getString(R.string.zoom_again), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
                again.setGravity(Gravity.CENTER);
                again.setMinHeight(CanvasLook.target());
                again.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(4), 0);
                again.setOnClickListener(v -> request());
                stale.addView(again);
                body.addView(stale, matchWrap());
            }
            if (data.partial()) {
                final TextView part = ZoomLook.text(a, a.getString(R.string.zoom_partial, data.coveredPercent()),
                        CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
                part.setPadding(0, 0, 0, CanvasLook.dp(8));
                body.addView(part);
            }
            if (level == LEAN) {
                lean();
            } else if (level == CLAIMS) {
                claims();
            } else {
                lines();
            }
            made();
        }
    }

    private String statusText() {
        if (data == null || level == FULL) {
            return a.getString(R.string.zoom_status_full);
        }
        final int wpm = ReadingSpeed.wpm(target.name);
        if (level == LEAN) {
            final String time = ZoomTime.lean(ZoomTime.minutes(data.leanChars(), wpm), ZoomTime.minutes(data.chars, wpm));
            return time.isEmpty() ? a.getString(R.string.zoom_status_lean_plain) : a.getString(R.string.zoom_status_lean, time);
        }
        if (level == CLAIMS) {
            return a.getString(R.string.zoom_status_claims,
                    ZoomTime.claims(ZoomTime.minutes(data.claimsChars(), wpm), data.partial()));
        }
        return a.getString(R.string.zoom_status_line);
    }

    /** @return the note's view, or null without a note */
    private TextView busy(String what, String note) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, CanvasLook.dp(48), 0, 0);
        box.addView(new ProgressBar(a), new LinearLayout.LayoutParams(CanvasLook.dp(28), CanvasLook.dp(28)));
        final TextView t = ZoomLook.text(a, what, CanvasLook.TEXT_BODY, 600, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, CanvasLook.dp(12), 0, 0);
        box.addView(t);
        TextView n = null;
        if (note != null) {
            n = ZoomLook.text(a, note, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, CanvasLook.dp(4), 0, 0);
            box.addView(n);
        }
        body.addView(box, matchWrap());
        return n;
    }

    /** Before the chapter has its levels: what they are, and that picking one sends the chapter once. */
    private void intro() {
        final LinearLayout card = CanvasCard.answers(a, a.getString(R.string.zoom_title), a.getString(R.string.zoom_intro));
        body.addView(card, matchWrap());
        if (range != null) {
            final TextView note = ZoomLook.text(a, a.getString(R.string.zoom_intro_send), 13, 400, CanvasLook.ink2());
            note.setLineSpacing(CanvasLook.dp(2), 1f);
            note.setPadding(0, CanvasLook.dp(12), 0, 0);
            body.addView(note);
        }
    }

    // --- Lean

    private void lean() {
        if (!data.hasLean()) {
            body.addView(ZoomLook.text(a, a.getString(R.string.zoom_no_lean), CanvasLook.TEXT_BODY, 400, CanvasLook.ink2()));
            return;
        }
        final TextView hint = ZoomLook.text(a, a.getString(R.string.zoom_t16_lean_hint), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        body.addView(hint);
        for (ZoomData.Section section : data.sections) {
            if (!section.hasLean()) {
                continue;
            }
            if (!section.question.isEmpty()) {
                body.addView(CanvasCard.answers(a, section.question), top(14));
            } else if (!section.title.isEmpty()) {
                body.addView(ZoomLook.mono(a, section.title, CanvasLook.TEXT_LABEL, CanvasLook.accent(), true), top(18));
            }
            for (ZoomData.Block block : section.blocks) {
                if (block.fold) {
                    body.addView(fold(block), top(12));
                    continue;
                }
                if (block.sentences.isEmpty()) {
                    continue;
                }
                if (!block.label.isEmpty()) {
                    body.addView(kept(block.label), wrapTop(12));
                }
                body.addView(paragraph(block), top(12));
                if (opened != null && block.sentences.contains(opened)) {
                    body.addView(original(opened), top(12));
                }
            }
        }
    }

    /** "KEPT · BEST EXAMPLE": why a paragraph keeps its detail. */
    private View kept(String label) {
        final TextView t = ZoomLook.mono(a, a.getString(R.string.zoom_kept, label), 10, CanvasLook.ink2(), false);
        t.setLetterSpacing(0.05f);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(CanvasLook.dp(22));
        t.setPadding(CanvasLook.dp(8), 0, CanvasLook.dp(8), 0);
        t.setBackground(CanvasParts.round(CanvasLook.surface2(), 0, CanvasLook.dp(6)));
        return t;
    }

    /** A paragraph of the lean version, in the book serif; a tap on a sentence opens its original. */
    private View paragraph(ZoomData.Block block) {
        final SpannableStringBuilder s = new SpannableStringBuilder();
        for (final ZoomData.Sentence x : block.sentences) {
            if (s.length() > 0) {
                s.append(' ');
            }
            final int from = s.length();
            s.append(x.text);
            s.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    opened = opened == x ? null : x;
                    render();
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    // a sentence of the book, not a link: no colour, no underline
                }
            }, from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (x == opened) {
                s.setSpan(new BackgroundColorSpan(CanvasLook.claimTint()), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        final TextView t = ZoomLook.book(a, s, 17, 1.5f, CanvasLook.ink());
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(0);
        return t;
    }

    /**
     * "AUTHOR'S ORIGINAL · ¶3" (canvas ZoomExpand): the passage a Lean sentence condenses, in the
     * author's own words as the chapter has them now, with Go to it and Copy quote. A sentence the
     * model gave no findable words for says so instead.
     */
    private View original(final ZoomData.Sentence x) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(CanvasLook.dp(14), CanvasLook.dp(4), CanvasLook.dp(4), CanvasLook.dp(14));
        card.setBackground(CanvasParts.round(CanvasLook.isNight() ? CanvasLook.accentTint() : 0xFFF7F8FF,
                CanvasLook.accentLine(), CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));

        ZoomText.Place place = null;
        if (x.located() && chapterText != null) {
            place = chapterText.locateFrom(x.quote, x.words);
        }
        final boolean missing = !x.located() || (chapterText != null && place == null);
        final boolean finding = x.located() && chapterText == null;

        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final String name = missing ? a.getString(R.string.zoom_original_missing)
                : place == null ? a.getString(R.string.zoom_original)
                : a.getString(R.string.zoom_original_para, para(place.para, place.paraTo));
        head.addView(ZoomLook.mono(a, name, CanvasLook.TEXT_LABEL, missing ? CanvasLook.muted() : CanvasLook.accent(), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView collapse = ZoomLook.text(a, "✕", 14, 400, CanvasLook.ink2());
        collapse.setGravity(Gravity.CENTER);
        collapse.setContentDescription(a.getString(R.string.zoom_original_collapse));
        collapse.setOnClickListener(v -> {
            opened = null;
            render();
        });
        head.addView(collapse, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        card.addView(head);

        if (missing || finding) {
            final TextView note = ZoomLook.text(a, a.getString(missing ? R.string.zoom_original_missing_note
                    : R.string.zoom_original_finding), 13, 400, CanvasLook.ink2());
            note.setPadding(0, 0, CanvasLook.dp(10), 0);
            card.addView(note);
            if (finding) {
                readText();
            }
            return card;
        }
        final String passage = chapterText.passage(place);
        final TextView words = ZoomLook.book(a, passage, 14, 1.5f, CanvasLook.ink());
        words.setPadding(0, 0, CanvasLook.dp(10), 0);
        words.setTextIsSelectable(true);
        card.addView(words);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, CanvasLook.dp(10), 0, 0);
        final TextView go = ZoomLook.button(a, a.getString(R.string.zoom_go_to_it), false);
        go.setTextColor(CanvasLook.accent());
        go.setOnClickListener(v -> jump(x.quote, x.page));
        buttons.addView(go);
        final TextView copy = ZoomLook.button(a, a.getString(R.string.zoom_copy_quote), false);
        copy.setOnClickListener(v -> {
            final android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(target.title, passage));
                Toast.makeText(a, R.string.copied_text, Toast.LENGTH_SHORT).show();
            }
        });
        final LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = CanvasLook.dp(8);
        buttons.addView(copy, clp);
        card.addView(buttons);
        return card;
    }

    /** Reads the chapter's text once, off the main thread, for the originals: nothing is sent. */
    private void readText() {
        if (readingText || range == null) {
            return;
        }
        readingText = true;
        final BookChapters.Range chapter = range;
        final int max = ZoomJob.maxChars(AiSettings.load(a));
        new Thread(() -> {
            ZoomText read = null;
            try (BookText text = BookText.open(target.path, target.width, target.height, target.fontSize)) {
                if (text != null) {
                    read = ZoomJob.read(text, chapter, max, () -> {
                    });
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            // a book that cannot be read gives an empty text: its originals then say "not found"
            final ZoomText done = read != null ? read : ZoomText.of(null);
            if (Apps.isDestroyedActivity(a)) {
                return;
            }
            a.runOnUiThread(() -> {
                chapterText = done;
                readingText = false;
                if (dialog.isShowing() && opened != null && job == null) {
                    render();
                }
            });
        }, "@T Zoom text").start();
    }

    /** "Folded: other trades, and why farming lags · 2 min": a tap reads it in the book. */
    private View fold(ZoomData.Block block) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(CanvasLook.target());
        row.setPadding(CanvasLook.dp(12), CanvasLook.dp(8), CanvasLook.dp(12), CanvasLook.dp(8));
        row.setBackground(CanvasParts.ripple(ZoomLook.dashed(CanvasLook.surface(), CanvasLook.lineStrong(),
                CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP))));
        final TextView more = ZoomLook.text(a, "›", 16, 400, CanvasLook.muted());
        more.setPadding(0, 0, CanvasLook.dp(10), 0);
        row.addView(more);
        final TextView what = ZoomLook.text(a, a.getString(R.string.zoom_folded, block.label), 13, 400, CanvasLook.ink2());
        row.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final int minutes = ZoomTime.foldMinutes(block, data.chars, ReadingSpeed.wpm(target.name));
        if (minutes > 0) {
            final TextView time = ZoomLook.mono(a, ZoomTime.duration(minutes), CanvasLook.TEXT_LABEL, CanvasLook.muted(), false);
            time.setAllCaps(false);
            time.setPadding(CanvasLook.dp(8), 0, 0, 0);
            row.addView(time);
        }
        if (block.located()) {
            row.setOnClickListener(v -> jump(block.quote, block.page));
        } else {
            row.setAlpha(0.6f);
        }
        return row;
    }

    // --- Claims

    private void claims() {
        if (data.claims.isEmpty()) {
            body.addView(ZoomLook.text(a, a.getString(R.string.zoom_no_claims), CanvasLook.TEXT_BODY, 400, CanvasLook.ink2()));
            return;
        }
        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final int n = data.claims.size();
        head.addView(ZoomLook.mono(a, a.getResources().getQuantityString(R.plurals.zoom_claims_count, n, n),
                        CanvasLook.TEXT_LABEL, CanvasLook.accent(), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(ZoomLook.text(a, a.getString(R.string.zoom_claims_tap), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()));
        head.setPadding(0, CanvasLook.dp(2), 0, CanvasLook.dp(2));
        body.addView(head, matchWrap());
        for (int i = 0; i < n; i++) {
            body.addView(claimRow(i), matchWrap());
            if (i < n - 1) {
                final View line = new View(a);
                line.setBackgroundColor(CanvasLook.surface2());
                body.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));
            }
        }
    }

    /** One row: its job in mono, the claim in the book serif, the example and the paragraph under it. */
    private View claimRow(int index) {
        final ZoomData.Claim claim = data.claims.get(index);
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, CanvasLook.dp(8), 0, CanvasLook.dp(8));
        row.setMinimumHeight(CanvasLook.target());
        row.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.dp(6))));

        final boolean main = claim.kind == ZoomKind.CLAIM;
        final TextView label = ZoomLook.mono(a, kindName(claim.kind, data.number(index)), CanvasLook.TEXT_LABEL,
                main ? CanvasLook.accent() : CanvasLook.muted(), main);
        label.setLetterSpacing(0.05f);
        label.setMinWidth(CanvasLook.dp(76));
        label.setPadding(0, CanvasLook.dp(3), CanvasLook.dp(12), 0);
        row.addView(label);

        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final boolean caveat = claim.kind == ZoomKind.CAVEAT;
        final TextView text = ZoomLook.book(a, claim.text, 15, 1.4f, caveat ? CanvasLook.ink2() : CanvasLook.ink());
        if (caveat) {
            text.setTypeface(ZoomLook.serif(a, true));
        }
        texts.addView(text);
        final StringBuilder under = new StringBuilder();
        if (!claim.example.isEmpty()) {
            under.append(a.getString(R.string.zoom_example, claim.example));
        }
        final String place = claim.located() ? para(claim.para, claim.para) : a.getString(R.string.ai_not_located);
        if (under.length() > 0) {
            under.append(" · ");
        }
        under.append(place);
        final TextView sub = ZoomLook.text(a, under, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        sub.setPadding(0, CanvasLook.dp(2), 0, 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (claim.located()) {
            final TextView go = ZoomLook.text(a, "›", 16, 400, CanvasLook.muted());
            go.setPadding(CanvasLook.dp(12), CanvasLook.dp(2), CanvasLook.dp(2), 0);
            row.addView(go);
            row.setOnClickListener(v -> jump(claim.quote, claim.page));
        }
        return row;
    }

    private String kindName(ZoomKind kind, int number) {
        switch (kind) {
            case EVIDENCE:
                return a.getString(R.string.zoom_kind_evidence);
            case WHY:
                return number > 0 ? a.getString(R.string.zoom_kind_why_n, number) : a.getString(R.string.zoom_kind_why);
            case CAVEAT:
                return a.getString(R.string.zoom_kind_caveat);
            case SO_WHAT:
                return a.getString(R.string.zoom_kind_so_what);
            default:
                return a.getString(R.string.zoom_kind_claim);
        }
    }

    // --- 1 line

    private void lines() {
        if (!data.line.isEmpty()) {
            body.addView(ZoomLook.mono(a, a.getString(R.string.zoom_one_line), CanvasLook.TEXT_LABEL, CanvasLook.accent(), true), top(10));
            body.addView(ZoomLook.book(a, data.line, 23, 1.45f, CanvasLook.ink()), top(10));
        }

        boolean any = false;
        for (ZoomData.Section s : data.sections) {
            any |= !s.line.isEmpty();
        }
        if (!any) {
            return;
        }
        final TextView per = ZoomLook.mono(a, a.getString(R.string.zoom_per_section), CanvasLook.TEXT_LABEL, CanvasLook.muted(), false);
        per.setPadding(CanvasLook.dp(4), 0, 0, 0);
        body.addView(per, top(28));
        final int pages = Math.max(1, range.to - range.from);
        final int here = data.sectionAt((float) (target.page - range.from) / pages);
        int number = 0;
        for (int i = 0; i < data.sections.size(); i++) {
            final ZoomData.Section s = data.sections.get(i);
            if (s.line.isEmpty()) {
                continue;
            }
            number++;
            body.addView(lineCard(s, number, i == here), top(number == 1 ? 8 : 6));
        }
        final TextView hint = ZoomLook.text(a, a.getString(R.string.zoom_line_hint), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        hint.setGravity(Gravity.CENTER);
        body.addView(hint, top(16));
    }

    /** A section in one line; the reader's own section in the accent, with "You're here". */
    private View lineCard(ZoomData.Section s, int number, boolean here) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        final int pad = CanvasLook.dp(12);
        card.setPadding(pad, pad, pad, pad);
        final int radius = CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP);
        if (here) {
            final android.graphics.drawable.GradientDrawable bg = CanvasParts.round(CanvasLook.accentTint(), 0, radius);
            bg.setStroke(Math.max(1, CanvasLook.dp(3) / 2), CanvasLook.accent());
            card.setBackground(CanvasParts.ripple(bg));
        } else {
            card.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), radius)));
        }

        final TextView badge = ZoomLook.text(a, String.valueOf(number), CanvasLook.TEXT_SMALL, 700,
                here ? CanvasLook.onAccent() : CanvasLook.ink2());
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(CanvasParts.round(here ? CanvasLook.accent() : CanvasLook.surface2(), 0, CanvasLook.dp(12)));
        final LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(CanvasLook.dp(24), CanvasLook.dp(24));
        blp.rightMargin = pad;
        card.addView(badge, blp);

        final LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(ZoomLook.book(a, s.line, 16, 1.5f, CanvasLook.ink()));
        final String place = s.located() ? para(s.para, s.paraTo) : "";
        final String under = here ? (place.isEmpty() ? a.getString(R.string.zoom_here)
                : a.getString(R.string.zoom_here) + " · " + place) : place;
        if (!under.isEmpty()) {
            final TextView sub = ZoomLook.text(a, under, CanvasLook.TEXT_SMALL, here ? 600 : 400,
                    here ? CanvasLook.accent() : CanvasLook.muted());
            sub.setPadding(0, CanvasLook.dp(4), 0, 0);
            texts.addView(sub);
        }
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (s.located()) {
            card.setOnClickListener(v -> jump(s.start, s.page));
        }
        return card;
    }

    // --- shared

    /** "Made by AI from the chapter's text. Make again": the request is sent again only when asked. */
    private void made() {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ZoomLook.text(a, a.getString(R.string.zoom_made), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView again = ZoomLook.text(a, a.getString(R.string.zoom_again), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
        again.setGravity(Gravity.CENTER);
        again.setMinHeight(CanvasLook.target());
        again.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(4), 0);
        again.setOnClickListener(v -> AiMenu.confirmRedo(a, R.string.zoom_redo_ask, this::request));
        row.addView(again);
        body.addView(row, top(20));
    }

    /** "¶3" or "¶1–3". */
    private String para(int from, int to) {
        return to > from ? a.getString(R.string.zoom_para_range, from, to) : a.getString(R.string.zoom_para, from);
    }

    /**
     * Closes the view and goes to a quote's place in the book (as the X-ray does): looked for in
     * the chapter's pages, starting where it was when the levels were made.
     *
     * @param page the page of the chapter it was on then, from 0; -1 unknown
     */
    private void jump(String quote, int page) {
        dialog.dismiss();
        if (range == null) {
            return;
        }
        final int pages = Math.max(1, range.to - range.from);
        AiJump.toQuote(dc, quote, range.from + data.pageIn(page, pages), range.from, range.to - 1);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams top(int dp) {
        final LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = CanvasLook.dp(dp);
        return lp;
    }

    private static LinearLayout.LayoutParams wrapTop(int dp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(dp);
        return lp;
    }
}
