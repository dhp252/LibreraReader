package com.foobnix.assist.xray;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.ai.AiJobs;
import com.foobnix.ai.AiJump;
import com.foobnix.ai.AiSettingsDialog;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.assist.Assist;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

/**
 * Argument X-ray of the chapter at the reader's page (canvas XrayMap, XrayFull): "Marks in text"
 * (the X-ray marks switch, the legend with what each job counts, the chapter's claims with Jump)
 * and "Argument map" (main claim → why → evidence, examples, caveats; a tap jumps to the mark).
 * "Rate the support" asks the AI once for the chapter how well each idea is supported; the weak
 * spot card ({@link XrayWeakCard}) opens from the map. Built from the stored key passages only.
 */
public final class XrayScreen implements XrayJob.Screen {

    /** The canvas's weak-spot orange (day) and its tint. */
    static final int WEAK_LINE = 0xFFF27420;
    static final int WEAK_TINT = 0xFFFFF1E6;

    private final Activity a;
    private final DocumentController dc;
    private final AiStore.Chapter chapter;
    private final XrayTree tree;
    private XrayRatings ratings;

    private Dialog dialog;
    private TextView tabMarks;
    private TextView tabMap;
    private LinearLayout body;
    private LinearLayout footer;
    private TextView status;
    private ProgressBar progress;
    private TextView rate;
    private TextView weak;
    private boolean onMap = true;
    private XrayJob job;

    private XrayScreen(Activity a, DocumentController dc, AiStore.Chapter chapter) {
        this.a = a;
        this.dc = dc;
        this.chapter = chapter;
        this.tree = XrayTree.build(XrayChapter.ideas(chapter));
    }

    /** The X-ray of the chapter at the reader's page (the ✦ menu's "Argument X-ray"). */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.XRAY.isOn() || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiJobs.Target t = AiJobs.Target.of(dc);
        final BookChapters.Range known = AiJobs.knownChapter(t);
        if (known != null) {
            show(a, dc, known.key);
            return;
        }
        new Thread(() -> {
            String key = null;
            try (BookText text = BookText.open(t.path, t.width, t.height, t.fontSize)) {
                if (text != null) {
                    key = BookChapters.chapterAt(AiJobs.toc(t, text), t.page, text.pageCount(), BookChapters.BLOCK_PAGES).key;
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            final String found = key;
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> show(a, dc, found));
            }
        }, "@T X-ray").start();
    }

    /** The X-ray of a chapter by its key passages key. */
    public static void show(Activity a, DocumentController dc, String key) {
        final AiStore.Chapter chapter = key == null ? null : AiHighlights.get().data().chapter(key);
        if (chapter == null || chapter.ideas.isEmpty()) {
            Toast.makeText(a, R.string.ai_ideas_none, Toast.LENGTH_LONG).show();
            return;
        }
        new XrayScreen(a, dc, chapter).open();
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
        page.addView(tabs());

        final ScrollView scroll = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(16));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        page.addView(footer());

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
        render();

        final String book = AiHighlights.bookName(dc.getCurrentBook().getPath());
        new Thread(() -> {
            final XrayRatings stored = XrayChapter.load(book, chapter, tree);
            if (stored != null && !Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> {
                    if (dialog.isShowing() && job == null) {
                        ratings = stored;
                        render();
                    }
                });
            }
        }, "@T X-ray").start();
    }

    private View topBar() {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text("‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(a.getString(R.string.xray_back));
        back.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        back.setOnClickListener(v -> dialog.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView title = text(a.getString(R.string.xray_title), CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        final TextView sub = text(chapter.title == null ? "" : chapter.title, CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    private View tabs() {
        final LinearLayout track = new LinearLayout(a);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(CanvasParts.segmentTrack());
        final int p = CanvasLook.dp(3);
        track.setPadding(p, p, p, p);
        tabMarks = tab(R.string.xray_tab_marks, false);
        tabMap = tab(R.string.xray_tab_map, true);
        track.addView(tabMarks, new LinearLayout.LayoutParams(0, CanvasLook.target(), 1));
        track.addView(tabMap, new LinearLayout.LayoutParams(0, CanvasLook.target(), 1));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        lp.setMargins(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(8));
        track.setLayoutParams(lp);
        return track;
    }

    private TextView tab(int name, boolean map) {
        final TextView t = text(a.getString(name), 13, 600, CanvasLook.ink2());
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> {
            onMap = map;
            render();
        });
        return t;
    }

    private View footer() {
        footer = new LinearLayout(a);
        footer.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        footer.setPadding(gutter, CanvasLook.dp(10), gutter, CanvasLook.dp(14));
        footer.setBackgroundColor(CanvasLook.bg());

        final LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(a);
        progress.setVisibility(View.GONE);
        line.addView(progress, new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
        status = text("", CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        status.setPadding(CanvasLook.dp(6), 0, 0, 0);
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        footer.addView(line);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, CanvasLook.dp(8), 0, 0);
        rate = button("", true);
        rate.setOnClickListener(v -> rate());
        weak = button(a.getString(R.string.xray_weak_spot_button), true);
        weak.setOnClickListener(v -> showWeak());
        final LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(rate, left);
        buttons.addView(weak, right);
        footer.addView(buttons);
        return footer;
    }

    /** Draws the chosen tab and the footer again. */
    private void render() {
        styleTab(tabMarks, !onMap);
        styleTab(tabMap, onMap);
        body.removeAllViews();
        if (onMap) {
            renderMap();
        } else {
            renderMarks();
        }
        footer.setVisibility(onMap ? View.VISIBLE : View.GONE);
        final boolean running = job != null;
        final boolean rated = ratings != null && !ratings.isEmpty();
        final int weakId = rated ? ratings.weakestId() : -1;
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        rate.setEnabled(!running);
        rate.setText(rated ? R.string.xray_rate_again : R.string.xray_rate);
        style(rate, !rated);
        weak.setVisibility(rated && weakId >= 0 ? View.VISIBLE : View.GONE);
        if (running) {
            status.setText(R.string.xray_rating);
        } else if (rated && ratings.ideasTime != chapter.time) {
            status.setText(R.string.xray_stale);
        } else if (!rated) {
            status.setText(R.string.xray_rate_note);
        } else if (status.getTag() == null) {
            status.setText("");
        }
        status.setTag(null);
    }

    private void styleTab(TextView t, boolean on) {
        t.setBackground(on ? CanvasParts.segmentOn() : null);
        t.setTextColor(on ? CanvasLook.ink() : CanvasLook.ink2());
    }

    // --- Argument map ---

    private void renderMap() {
        final int weakId = ratings == null ? -1 : ratings.weakestId();
        for (XrayTree.Node node : tree.order) {
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = CanvasLook.dp(24) * Math.min(3, node.depth);
            lp.topMargin = CanvasLook.dp(node == tree.order.get(0) ? 8 : 12);
            body.addView(box(node, node.idea.id == weakId), lp);
        }

        if (ratings != null && !ratings.isEmpty()) {
            final LinearLayout chips = new LinearLayout(a);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            final int weakCount = weakId >= 0 ? Math.max(1, ratings.count(XrayRatings.Rating.WEAK)) : 0;
            final int questioned = ratings.count(XrayRatings.Rating.QUESTIONED);
            if (weakCount > 0) {
                final TextView chip = chip(a.getResources().getQuantityString(R.plurals.xray_weak_spots, weakCount, weakCount),
                        weakTint(), weakText());
                chip.setOnClickListener(v -> showWeak());
                chips.addView(chip);
            }
            if (questioned > 0) {
                final TextView chip = chip(a.getResources().getQuantityString(R.plurals.xray_questioned, questioned, questioned),
                        CanvasLook.surface2(), CanvasLook.ink2());
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = CanvasLook.dp(6);
                chips.addView(chip, lp);
            }
            if (chips.getChildCount() > 0) {
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = CanvasLook.dp(14);
                body.addView(chips, lp);
            }
        }
        final TextView hint = text(a.getString(R.string.xray_tap_hint), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        hint.setPadding(0, CanvasLook.dp(8), 0, 0);
        body.addView(hint);
    }

    /** One box of the map, as the canvas draws its kind; a tap goes to the idea's first mark. */
    private View box(XrayTree.Node node, boolean weakSpot) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setMinimumHeight(CanvasLook.dp(52));
        final boolean main = node.role == XrayTree.Role.MAIN_CLAIM;
        box.setPadding(CanvasLook.dp(main ? 14 : 12), CanvasLook.dp(main ? 10 : 8), CanvasLook.dp(12), CanvasLook.dp(main ? 10 : 8));

        final int radius = CanvasLook.dp(main ? CanvasLook.RADIUS_DP : CanvasLook.RADIUS_SMALL_DP);
        final GradientDrawable bg;
        int labelColor = CanvasLook.muted();
        int textColor = CanvasLook.ink();
        if (main) {
            bg = CanvasParts.round(CanvasLook.accent(), 0, radius);
            textColor = CanvasLook.onAccent();
            labelColor = (CanvasLook.onAccent() & 0x00FFFFFF) | 0xC0000000;
        } else if (weakSpot) {
            bg = CanvasParts.round(weakTint(), 0, radius);
            bg.setStroke(Math.max(1, CanvasLook.dp(1) * 3 / 2), weakLine(), CanvasLook.dp(5), CanvasLook.dp(3));
            labelColor = weakText();
        } else if (node.role == XrayTree.Role.CAVEAT) {
            bg = CanvasParts.round(CanvasLook.bg(), 0, radius);
            bg.setStroke(Math.max(1, CanvasLook.dp(1)), CanvasLook.isNight() ? CanvasLook.LINE_STRONG_N : 0xFFA5ABB6,
                    CanvasLook.dp(4), CanvasLook.dp(3));
        } else if (node.role == XrayTree.Role.EVIDENCE || node.role == XrayTree.Role.EXAMPLE) {
            bg = CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), radius);
        } else {
            bg = CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), radius);
        }
        box.setBackground(CanvasParts.ripple(bg));

        final TextView label = mono(labelText(node, weakSpot), labelColor, main || weakSpot);
        box.addView(label);
        final TextView idea = text(node.idea.text, 14, main ? 500 : 400, textColor);
        if (node.role == XrayTree.Role.CAVEAT) {
            idea.setTypeface(idea.getTypeface(), Typeface.ITALIC);
        }
        box.addView(idea);
        if (weakSpot) {
            final XrayRatings.Entry e = ratings.get(node.idea.id);
            if (e != null && !e.why.isEmpty()) {
                box.addView(text(e.why, CanvasLook.TEXT_SMALL, 400, CanvasLook.ink2()));
            }
        }
        if (!node.idea.marked) {
            box.setAlpha(0.55f);
        }
        if (node.idea.first != null || node.idea.firstPage >= 0) {
            box.setOnClickListener(v -> jump(node.idea));
        }
        return box;
    }

    private String labelText(XrayTree.Node node, boolean weakSpot) {
        final StringBuilder s = new StringBuilder(a.getString(roleName(node.role)));
        if (node.idea.firstPage >= 0) {
            s.append(" · ").append(a.getString(R.string.xray_page, node.idea.firstPage + 1));
        }
        if (weakSpot) {
            s.append(" · ").append(a.getString(R.string.xray_rating_weak_spot));
        } else if (ratings != null && ratings.rating(node.idea.id) != null) {
            s.append(" · ").append(a.getString(ratingName(ratings.rating(node.idea.id))));
        }
        if (!node.idea.marked) {
            s.append(" · ").append(a.getString(R.string.ai_not_located));
        }
        return s.toString();
    }

    static int roleName(XrayTree.Role role) {
        switch (role) {
            case MAIN_CLAIM:
                return R.string.xray_role_main_claim;
            case CLAIM:
                return R.string.xray_role_claim;
            case WHY:
                return R.string.xray_role_why;
            case EVIDENCE:
                return R.string.xray_role_evidence;
            case EXAMPLE:
                return R.string.xray_role_example;
            case CAVEAT:
                return R.string.xray_role_caveat;
            case CONCEPT:
                return R.string.xray_role_concept;
            default:
                return R.string.xray_role_apply;
        }
    }

    /** The map's name of a rating ("source questioned"). */
    static int ratingName(XrayRatings.Rating r) {
        switch (r) {
            case STRONG:
                return R.string.xray_rating_strong;
            case FAIR:
                return R.string.xray_rating_fair;
            case OBSERVATION:
                return R.string.xray_rating_observation;
            case QUESTIONED:
                return R.string.xray_rating_questioned;
            default:
                return R.string.xray_rating_weak;
        }
    }

    // --- Marks in text ---

    private void renderMarks() {
        final TextView about = text(a.getString(XrayStyle.marksShown() ? R.string.xray_marks_on : R.string.xray_marks_off),
                CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        about.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(4));
        body.addView(about);

        final Switch marks = new Switch(a);
        marks.setText(R.string.xray_marks_switch);
        CanvasParts.text(marks, CanvasLook.TEXT_BODY, 500);
        marks.setMinHeight(CanvasLook.target());
        marks.setChecked(XrayStyle.marksShown());
        marks.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            XrayStyle.setMarksShown(on);
            render();
        });
        body.addView(marks);

        final XrayLegendView legend = new XrayLegendView(a);
        legend.setPadding(0, legend.getPaddingTop(), 0, legend.getPaddingBottom());
        legend.setCounts(tree.counts());
        body.addView(legend);

        if (!AiHighlights.get().isVisible()) {
            final TextView hidden = text(a.getString(R.string.xray_hidden), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            hidden.setPadding(0, 0, 0, CanvasLook.dp(8));
            body.addView(hidden);
        }

        for (XrayTree.Node node : tree.roots) {
            if (node.role != XrayTree.Role.MAIN_CLAIM && node.role != XrayTree.Role.CLAIM) {
                continue;
            }
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = CanvasLook.dp(12);
            body.addView(claimCard(node), lp);
        }
    }

    /** "THIS PASSAGE SUPPORTS" in the canvas: a claim, its words in the claim tint, Jump. */
    private View claimCard(XrayTree.Node node) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.accentLine(), CanvasLook.radius()));
        final int p = CanvasLook.dp(12);
        card.setPadding(p, CanvasLook.dp(6), p, CanvasLook.dp(10));

        final LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        String label = a.getString(roleName(node.role));
        if (node.idea.firstPage >= 0) {
            label += " · " + a.getString(R.string.xray_page, node.idea.firstPage + 1);
        }
        head.addView(mono(label, CanvasLook.accent(), true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (node.idea.first != null || node.idea.firstPage >= 0) {
            final TextView jump = text(a.getString(R.string.xray_jump), CanvasLook.TEXT_SMALL, 600, CanvasLook.accent());
            jump.setGravity(Gravity.CENTER);
            jump.setPadding(CanvasLook.dp(8), 0, CanvasLook.dp(4), 0);
            jump.setMinHeight(CanvasLook.target());
            jump.setOnClickListener(v -> jump(node.idea));
            head.addView(jump);
        }
        card.addView(head);

        final TextView idea = text(node.idea.text, 14, 600, CanvasLook.ink());
        card.addView(idea);
        if (node.idea.first != null) {
            final SpannableString words = new SpannableString("“" + node.idea.first.trim() + "”");
            words.setSpan(new BackgroundColorSpan(CanvasLook.claimTint()), 0, words.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            final TextView quote = text("", 14, 400, CanvasLook.ink());
            quote.setTypeface(Typeface.SERIF);
            quote.setLineSpacing(0, 1.35f);
            quote.setText(words);
            quote.setPadding(0, CanvasLook.dp(6), 0, 0);
            card.addView(quote);
        }
        int support = 0;
        int caveats = 0;
        for (XrayTree.Node child : node.children) {
            if (child.role == XrayTree.Role.CAVEAT) {
                caveats++;
            } else {
                support++;
            }
        }
        final TextView under = text(a.getString(R.string.xray_supported_by, support, caveats), CanvasLook.TEXT_SMALL, 400,
                CanvasLook.muted());
        under.setPadding(0, CanvasLook.dp(6), 0, 0);
        card.addView(under);
        return card;
    }

    // --- rating ---

    private void rate() {
        if (job != null) {
            return;
        }
        job = new XrayJob(a, AiJobs.Target.of(dc), chapter, this);
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
    public void onRated(XrayRatings rated) {
        job = null;
        ratings = rated.keep(tree.ids());
        if (dialog.isShowing()) {
            render();
            if (ratings.weakestId() >= 0) {
                showWeak();
            }
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        if (dialog.isShowing()) {
            render();
            status.setText(message);
            status.setTag(Boolean.TRUE);
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        if (dialog.isShowing()) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }

    private void showWeak() {
        if (ratings != null && ratings.weakestId() >= 0) {
            // Debate it (canvas XrayWeak, Assist.RECALL): the debate on the weak idea
            XrayWeakCard.show(a, tree, ratings, this::jump, Assist.RECALL.isOn() ? this::debate : null);
        }
    }

    /** Opens the debate on an idea over the X-ray, starting from the weak spot's title as the reader's position. */
    private void debate(XrayIdea idea) {
        com.foobnix.assist.debate.DebateScreen.show(a, dc, chapter, idea.id, ratings == null ? "" : ratings.weakTitle,
                () -> dialog.dismiss());
    }

    /** Closes the X-ray and goes to the idea's first mark (as All ideas does). */
    void jump(XrayIdea idea) {
        if (dialog != null) {
            dialog.dismiss();
        }
        AiJump.toQuote(dc, idea.first, idea.firstPage, -1, -1);
    }

    // --- parts ---

    static int weakLine() {
        return CanvasLook.isNight() ? CanvasLook.SENSOR_N : WEAK_LINE;
    }

    static int weakTint() {
        return CanvasLook.isNight() ? CanvasLook.SENSOR_TINT_N : WEAK_TINT;
    }

    static int weakText() {
        return CanvasLook.sensor();
    }

    private TextView text(String s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(a, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView mono(String s, int color, boolean medium) {
        final TextView t = new TextView(a);
        t.setText(s);
        t.setTypeface(CanvasLook.mono(a, medium ? 500 : 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        t.setLetterSpacing(0.05f);
        t.setAllCaps(true);
        t.setTextColor(color);
        return t;
    }

    private TextView chip(String s, int fill, int color) {
        final TextView t = text(s, CanvasLook.TEXT_SMALL, 600, color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(CanvasLook.dp(28));
        t.setPadding(CanvasLook.dp(10), 0, CanvasLook.dp(10), 0);
        t.setBackground(CanvasParts.round(fill, 0, CanvasLook.dp(8)));
        return t;
    }

    private TextView button(String s, boolean primary) {
        final TextView t = text(s, CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        style(t, primary);
        return t;
    }

    private static void style(TextView t, boolean primary) {
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        t.setTextColor(primary ? CanvasLook.onAccent() : CanvasLook.ink());
    }
}
