package com.foobnix.assist.debate;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import com.foobnix.assist.AssistStore;
import com.foobnix.assist.xray.XrayChapter;
import com.foobnix.assist.xray.XrayIdea;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.canvas.CanvasParts;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debate the author (canvas "Debate the author", Assist.RECALL): the reader picks a claim of the
 * chapter, states a position in one line and argues it for 3 rounds. Each round is the reader's
 * message, the author's reply (the AI in the author's voice; only words found in the book stand
 * in quotes, with a source line that goes to them) and a referee's verdict; then a closing card
 * with the score and where the two really disagree. Built from the chapter's stored key passages.
 * Only the result is stored ({@link DebateResult}); a debate left half way is kept in memory and
 * goes on when the same claim is opened again.
 */
public final class DebateScreen implements DebateJob.Screen {

    /** A debate left before its last round, for the book it is about (in memory only). */
    private static DebateSession kept;
    private static String keptBook;

    private final Activity a;
    private final DocumentController dc;
    private final AiStore.Chapter chapter;
    private final List<XrayIdea> ideas;
    private final String book;
    private final String bookTitle;
    /** The idea the debate opens on (from the X-ray's weak spot), or -1 to pick one. */
    private final int startIdea;
    private final String startPosition;
    /** Runs before a source line leaves for the book: closes the screen the debate was opened from. */
    private final Runnable beforeJump;

    private DebateAuthor author = DebateAuthor.of(null);
    private final Map<Integer, DebateResult> past = new HashMap<>();
    private boolean loaded;
    /** Null while the claim is picked. */
    private DebateSession session;
    /** The reader's message that is being answered. */
    private String pending;
    private String error;
    private DebateJob job;

    private Dialog dialog;
    private TextView subtitle;
    private LinearLayout positions;
    private TextView youText;
    private TextView authorLabel;
    private TextView authorText;
    private ScrollView scroll;
    private LinearLayout body;
    private TextView footer;
    private LinearLayout inputBar;
    private EditText input;
    private TextView send;

    private DebateScreen(Activity a, DocumentController dc, AiStore.Chapter chapter, int startIdea, String startPosition,
                         Runnable beforeJump) {
        this.a = a;
        this.dc = dc;
        this.chapter = chapter;
        this.ideas = XrayChapter.ideas(chapter);
        this.book = AiHighlights.bookName(dc.getCurrentBook().getPath());
        this.bookTitle = AiJobs.Target.of(dc).title;
        this.startIdea = startIdea;
        this.startPosition = startPosition == null ? "" : startPosition;
        this.beforeJump = beforeJump;
    }

    /** The ✦ menu's "Debate the author": the claims of the chapter at the reader's page. */
    public static void showHere(final Activity a, final DocumentController dc) {
        if (!Assist.RECALL.isOn() || dc == null || dc.getCurrentBook() == null) {
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
        }, "@T Debate").start();
    }

    /** The claims of a chapter by its key passages key; without key passages it says they are needed first. */
    public static void show(Activity a, DocumentController dc, String key) {
        if (!Assist.RECALL.isOn() || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final AiStore.Chapter chapter = key == null ? null : AiHighlights.get().data().chapter(key);
        if (chapter == null || chapter.ideas.isEmpty()) {
            Toast.makeText(a, R.string.ai_ideas_none, Toast.LENGTH_LONG).show();
            return;
        }
        new DebateScreen(a, dc, chapter, -1, "", null).open();
    }

    /**
     * The debate on one idea of a chapter (the X-ray's "Debate it").
     *
     * @param position   the reader's position to start from (the weak spot's title), or empty
     * @param beforeJump closes the caller's screen before a source line goes to the book, or null
     */
    public static void show(Activity a, DocumentController dc, AiStore.Chapter chapter, int ideaId, String position,
                            Runnable beforeJump) {
        if (!Assist.RECALL.isOn() || dc == null || dc.getCurrentBook() == null || chapter == null
                || chapter.ideas.isEmpty()) {
            return;
        }
        new DebateScreen(a, dc, chapter, ideaId, position, beforeJump).open();
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
        page.addView(positions());

        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        body.setPadding(gutter, CanvasLook.dp(12), gutter, CanvasLook.dp(12));
        scroll.addView(body);
        // the chat stays at its newest message when it grows, and when the keyboard opens
        body.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (session != null && !session.readerPosition.isEmpty() && b - t != ob - ot) {
                scroll.post(() -> scroll.smoothScrollTo(0, body.getHeight()));
            }
        });
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // the canvas's note, always shown: which words are the author's
        footer = text("", CanvasLook.TEXT_LABEL, 400, CanvasLook.muted());
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(8));
        page.addView(footer);

        page.addView(inputBar());

        dialog.setContentView(page);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        com.foobnix.android.utils.SafeArea.fit(dialog, page, dark);
        dialog.setOnDismissListener(d -> stop());
        dialog.show();
        render();

        // the author's name from the library's record, and the chapter's earlier results
        final String path = dc.getCurrentBook().getPath();
        new Thread(() -> {
            final DebateAuthor named = DebateAuthor.of(authorOf(path));
            final Map<Integer, DebateResult> results = new HashMap<>();
            try {
                for (Map.Entry<String, JSONObject> e : AssistStore.get().items(book, DebateResult.prefix(chapter.key)).entrySet()) {
                    final DebateResult r = DebateResult.fromJson(e.getValue());
                    final int id = DebateResult.ideaOf(e.getKey());
                    if (r != null && id >= 0) {
                        results.put(id, r);
                    }
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            if (!Apps.isDestroyedActivity(a)) {
                a.runOnUiThread(() -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    author = named;
                    past.putAll(results);
                    loaded = true;
                    final XrayIdea start = startIdea < 0 ? null : idea(startIdea);
                    if (start != null) {
                        choose(start, startPosition);
                    } else {
                        render();
                    }
                });
            }
        }, "@T Debate").start();
    }

    /** The author as the library's record of the book has it, or empty. */
    private static String authorOf(String path) {
        try {
            final com.foobnix.dao2.FileMeta meta = com.foobnix.ui2.AppDB.get().load(path);
            return meta == null || meta.getAuthor() == null ? "" : meta.getAuthor();
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    private XrayIdea idea(int id) {
        for (XrayIdea idea : ideas) {
            if (idea.id == id) {
                return idea;
            }
        }
        return null;
    }

    private View topBar() {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(CanvasLook.dp(8), CanvasLook.dp(6), CanvasLook.dp(8), CanvasLook.dp(6));
        final TextView back = text("‹", 26, 400, CanvasLook.ink());
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(a.getString(R.string.debate_back));
        back.setBackground(CanvasParts.ripple(CanvasParts.round(0, 0, CanvasLook.radius())));
        back.setOnClickListener(v -> back());
        bar.addView(back, new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));

        final LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView title = text(a.getString(R.string.debate_title), CanvasLook.TEXT_TITLE, 600, CanvasLook.ink());
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        subtitle = text("", CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(a), new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target()));
        return bar;
    }

    /** The two positions at the top: YOU, and the author with "AI" beside the name. */
    private View positions() {
        positions = new LinearLayout(a);
        positions.setOrientation(LinearLayout.HORIZONTAL);
        final int gutter = CanvasLook.dp(CanvasLook.GUTTER_DP);
        positions.setPadding(gutter, CanvasLook.dp(4), gutter, CanvasLook.dp(10));

        final LinearLayout you = positionCell(CanvasLook.accentTint());
        you.addView(mono(a.getString(R.string.debate_you), CanvasLook.accent(), false));
        youText = positionText();
        you.addView(youText);
        positions.addView(you, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        final LinearLayout other = positionCell(CanvasLook.surface2());
        authorLabel = mono("", CanvasLook.ink2(), false);
        other.addView(authorLabel);
        authorText = positionText();
        other.addView(authorText);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = CanvasLook.dp(8);
        positions.addView(other, lp);
        return positions;
    }

    private LinearLayout positionCell(int fill) {
        final LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setBackground(CanvasParts.round(fill, 0, CanvasLook.dp(CanvasLook.RADIUS_SMALL_DP)));
        cell.setPadding(CanvasLook.dp(10), CanvasLook.dp(8), CanvasLook.dp(10), CanvasLook.dp(8));
        return cell;
    }

    private TextView positionText() {
        final TextView t = text("", CanvasLook.TEXT_SMALL, 600, CanvasLook.ink());
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, CanvasLook.dp(2), 0, 0);
        return t;
    }

    private View inputBar() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final View line = new View(a);
        line.setBackgroundColor(CanvasLook.line());
        box.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, CanvasLook.dp(1))));

        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(12));

        input = new EditText(a);
        CanvasParts.text(input, 14, 400);
        input.setHintTextColor(CanvasLook.muted());
        input.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.dp(22)));
        input.setPadding(CanvasLook.dp(14), CanvasLook.dp(10), CanvasLook.dp(14), CanvasLook.dp(10));
        input.setMinHeight(CanvasLook.target());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DebateSession.MESSAGE_CHARS)});
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        send = text("↑", 20, 600, CanvasLook.onAccent());
        send.setGravity(Gravity.CENTER);
        send.setContentDescription(a.getString(R.string.debate_send));
        send.setBackground(CanvasParts.round(CanvasLook.accent(), 0, CanvasLook.dp(22)));
        send.setOnClickListener(v -> send());
        final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(CanvasLook.target(), CanvasLook.target());
        sp.leftMargin = CanvasLook.dp(8);
        row.addView(send, sp);
        box.addView(row);
        inputBar = box;
        return box;
    }

    // --- what the reader does ---

    /** The top bar's back: from a debate not yet begun to the claims, else out. */
    private void back() {
        if (startIdea < 0 && session != null && session.rounds.isEmpty() && job == null) {
            session = null;
            error = null;
            input.setText("");
            render();
            return;
        }
        dialog.dismiss();
    }

    /** Opens the debate on an idea: the one left half way on it, or a new one. */
    private void choose(XrayIdea idea, String position) {
        final DebateSession old = kept;
        if (old != null && book.equals(keptBook) && old.chapterKey.equals(chapter.key) && old.claim.id == idea.id
                && old.claim.text.equals(idea.text) && !old.finished() && !old.rounds.isEmpty()) {
            session = old;
            input.setText("");
        } else {
            session = new DebateSession(bookTitle, chapter.key, chapter.title, author, ideas, idea);
            input.setText(DebateSession.cut(position, DebateSession.POSITION_CHARS));
            input.setSelection(input.getText().length());
        }
        error = null;
        render();
    }

    private void send() {
        if (job != null || session == null || session.finished() || !Assist.RECALL.isOn()) {
            return;
        }
        final String said = input.getText().toString().replaceAll("\\s+", " ").trim();
        if (said.isEmpty()) {
            return;
        }
        if (session.readerPosition.isEmpty()) {
            session.readerPosition = DebateSession.cut(said, DebateSession.POSITION_CHARS);
            input.setText("");
            render();
            return;
        }
        pending = DebateSession.cut(said, DebateSession.MESSAGE_CHARS);
        error = null;
        input.setText("");
        job = new DebateJob(a, AiJobs.Target.of(dc), session, pending, this);
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
    public void onRound(DebateRound round, String position, String crux) {
        job = null;
        pending = null;
        session.add(round, position, crux);
        if (session.finished()) {
            forget(session);
            save(session);
        } else {
            kept = session;
            keptBook = book;
        }
        if (dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onError(String message) {
        job = null;
        giveBack();
        error = message;
        if (dialog.isShowing()) {
            render();
        }
    }

    @Override
    public void onNeedSettings() {
        job = null;
        giveBack();
        if (dialog.isShowing()) {
            render();
        }
        AiSettingsDialog.show(a, null);
    }

    /** The message that got no answer goes back into the field. */
    private void giveBack() {
        if (pending != null && input.getText().length() == 0) {
            input.setText(pending);
            input.setSelection(input.getText().length());
        }
        pending = null;
    }

    private static void forget(DebateSession s) {
        if (kept == s) {
            kept = null;
            keptBook = null;
        }
    }

    /** Stores the result of a finished debate: positions, verdicts, date. Not the chat. */
    private void save(DebateSession s) {
        if (!Assist.RECALL.isOn()) {
            return;
        }
        final DebateResult result = s.result(System.currentTimeMillis());
        past.put(s.claim.id, result);
        new Thread(() -> {
            try {
                AssistStore.get().put(book, DebateResult.key(s.chapterKey, s.claim.id), result.toJson());
            } catch (Exception e) {
                LOG.e(e);
                if (!Apps.isDestroyedActivity(a)) {
                    a.runOnUiThread(() -> Toast.makeText(a, R.string.debate_not_saved, Toast.LENGTH_LONG).show());
                }
            }
        }, "@T Debate").start();
    }

    /** Leaves for the book's words behind a source line; a debate half way stays for later. */
    private void jump(DebateRound round) {
        dialog.dismiss();
        if (beforeJump != null) {
            beforeJump.run();
        }
        AiJump.toQuote(dc, round.source, round.sourcePage, -1, -1);
    }

    // --- drawing ---

    private void render() {
        final boolean picking = session == null;
        final boolean stating = !picking && session.readerPosition.isEmpty();
        final boolean over = !picking && session.finished();
        final String who = who();

        if (picking || stating) {
            subtitle.setText(chapter.title == null ? "" : chapter.title);
        } else if (over) {
            subtitle.setText(R.string.debate_final_label);
        } else {
            subtitle.setText(a.getString(R.string.debate_round, session.round(), DebateSession.ROUNDS));
        }
        footer.setText(author.known ? a.getString(R.string.debate_footer, author.shortName)
                : a.getString(R.string.debate_footer_unknown));
        positions.setVisibility(picking || stating ? View.GONE : View.VISIBLE);
        inputBar.setVisibility(!loaded || picking || over ? View.GONE : View.VISIBLE);
        final boolean busy = job != null;
        input.setEnabled(!busy);
        send.setEnabled(!busy);
        send.setAlpha(busy ? 0.4f : 1f);

        body.removeAllViews();
        if (!loaded) {
            return;
        }
        if (picking) {
            renderPick();
            return;
        }
        if (stating) {
            input.setHint(R.string.debate_position_hint);
            renderPosition(who);
            return;
        }
        input.setHint(session.rounds.isEmpty() ? R.string.debate_first_hint : R.string.debate_next_hint);
        youText.setText(session.readerPosition);
        authorLabel.setText(a.getString(R.string.debate_author_ai, who));
        authorText.setText(session.authorPosition.isEmpty() ? session.claim.text : session.authorPosition);
        renderChat(who);
    }

    /** The author's short name, or "The author". */
    private String who() {
        return author.known ? author.shortName : a.getString(R.string.debate_the_author);
    }

    /** The name inside a sentence. */
    private String whoMid() {
        return author.known ? author.shortName : a.getString(R.string.debate_the_author_mid);
    }

    private String score(DebateResult r, String who) {
        String s = a.getString(R.string.debate_score, r.points(DebateReply.Point.READER), who,
                r.points(DebateReply.Point.AUTHOR));
        final int draws = r.points(DebateReply.Point.DRAW);
        if (draws > 0) {
            s += " · " + a.getResources().getQuantityString(R.plurals.debate_draws, draws, draws);
        }
        return s;
    }

    /** The chapter's main ideas, one card each; a tap opens the debate on it. */
    private void renderPick() {
        body.addView(text(a.getString(R.string.debate_pick_title), CanvasLook.TEXT_HEADING, 600, CanvasLook.ink()));
        final TextView note = text(a.getString(R.string.debate_pick_note), 14, 400, CanvasLook.muted());
        note.setPadding(0, CanvasLook.dp(4), 0, CanvasLook.dp(4));
        body.addView(note);
        final String who = who();
        for (final XrayIdea idea : DebateSession.claims(ideas)) {
            final LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.lineStrong(), CanvasLook.radius())));
            final int p = CanvasLook.dp(12);
            card.setPadding(p, CanvasLook.dp(10), p, CanvasLook.dp(10));
            card.setMinimumHeight(CanvasLook.dp(52));
            if (idea.firstPage >= 0) {
                card.addView(mono(a.getString(R.string.debate_source_page, idea.firstPage + 1), CanvasLook.muted(), false));
            }
            card.addView(text(idea.text, 14, 500, CanvasLook.ink()));
            final DebateSession old = kept;
            if (old != null && book.equals(keptBook) && old.chapterKey.equals(chapter.key) && old.claim.id == idea.id
                    && old.claim.text.equals(idea.text) && !old.finished() && !old.rounds.isEmpty()) {
                card.addView(small(a.getString(R.string.debate_in_progress, old.round(), DebateSession.ROUNDS), CanvasLook.accent()));
            } else if (past.get(idea.id) != null) {
                card.addView(small(a.getString(R.string.debate_last_time, score(past.get(idea.id), who)), CanvasLook.muted()));
            }
            card.setOnClickListener(v -> choose(idea, ""));
            body.addView(card, spaced(10));
        }
    }

    /** The claim and what a debate is, above the field for the reader's position. */
    private void renderPosition(String who) {
        final XrayIdea claim = session.claim;
        body.addView(mono(a.getString(R.string.debate_claim_label), CanvasLook.accent(), true));
        final TextView idea = text(claim.text, 18, 600, CanvasLook.ink());
        idea.setPadding(0, CanvasLook.dp(4), 0, 0);
        body.addView(idea);
        if (claim.first != null && !claim.first.trim().isEmpty()) {
            final SpannableStringBuilder words = new SpannableStringBuilder("“" + claim.first.trim() + "”");
            words.setSpan(new BackgroundColorSpan(CanvasLook.claimTint()), 0, words.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            final TextView quote = text("", 14, 400, CanvasLook.ink());
            quote.setTypeface(Typeface.SERIF);
            quote.setLineSpacing(0, 1.35f);
            quote.setText(words);
            body.addView(quote, spaced(10));
        }
        final TextView intro = text(a.getString(R.string.debate_position_intro, whoMid()), 14, 400, CanvasLook.muted());
        intro.setLineSpacing(0, 1.2f);
        body.addView(intro, spaced(14));
        final DebateResult last = past.get(claim.id);
        if (last != null) {
            body.addView(small(a.getString(R.string.debate_last_time, score(last, who)), CanvasLook.muted()), spaced(8));
        }
    }

    private void renderChat(String who) {
        if (session.rounds.isEmpty() && pending == null) {
            final TextView opening = text(a.getString(R.string.debate_opening), 14, 400, CanvasLook.muted());
            opening.setGravity(Gravity.CENTER);
            body.addView(opening, spaced(4));
        }
        int n = 1;
        for (DebateRound round : session.rounds) {
            body.addView(readerBubble(round.message), spaced(n == 1 ? 0 : 12));
            body.addView(authorRow(round), spaced(12));
            body.addView(refereeCard(n, round), spaced(12));
            n++;
        }
        if (pending != null) {
            body.addView(readerBubble(pending), spaced(session.rounds.isEmpty() ? 0 : 12));
            final LinearLayout waiting = new LinearLayout(a);
            waiting.setOrientation(LinearLayout.HORIZONTAL);
            waiting.setGravity(Gravity.CENTER_VERTICAL);
            waiting.addView(new ProgressBar(a), new LinearLayout.LayoutParams(CanvasLook.dp(18), CanvasLook.dp(18)));
            final TextView label = text(a.getString(R.string.debate_answering, who), CanvasLook.TEXT_SMALL, 400, CanvasLook.muted());
            label.setPadding(CanvasLook.dp(8), 0, 0, 0);
            waiting.addView(label);
            body.addView(waiting, spaced(12));
        }
        if (error != null) {
            body.addView(text(error, CanvasLook.TEXT_SMALL, 400, CanvasLook.sensor()), spaced(12));
        }
        if (session.finished()) {
            body.addView(closingCard(who), spaced(12));
        }
    }

    /** The reader's message: an accent bubble on the right. */
    private View readerBubble(String message) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        final TextView bubble = text(message, 14, 400, CanvasLook.onAccent());
        bubble.setLineSpacing(0, 1.2f);
        bubble.setMaxWidth(CanvasLook.dp(290));
        bubble.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));
        bubble.setBackground(bubble(CanvasLook.accent(), 16, 16, 4, 16));
        row.addView(bubble);
        return row;
    }

    /** The author's reply: the avatar, the reply with the book's words set apart, the source line. */
    private View authorRow(final DebateRound round) {
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView avatar = text(author.known ? author.initials : a.getString(R.string.debate_author_initial), 11, 700,
                CanvasLook.bg());
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(CanvasParts.round(CanvasLook.ink(), 0, CanvasLook.dp(14)));
        row.addView(avatar, new LinearLayout.LayoutParams(CanvasLook.dp(28), CanvasLook.dp(28)));

        final LinearLayout bubble = new LinearLayout(a);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackground(bubble(CanvasLook.surface2(), 4, 16, 16, 16));
        bubble.setPadding(CanvasLook.dp(12), CanvasLook.dp(10), CanvasLook.dp(12), CanvasLook.dp(10));

        final SpannableStringBuilder said = new SpannableStringBuilder();
        for (DebateQuotes.Part part : round.reply) {
            final int from = said.length();
            said.append(part.quoted ? "“" + part.text + "”" : part.text);
            if (part.quoted) {
                said.setSpan(new TypefaceSpan("serif"), from, said.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                said.setSpan(new StyleSpan(Typeface.ITALIC), from, said.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        final TextView reply = text("", 14, 400, CanvasLook.ink());
        reply.setLineSpacing(0, 1.2f);
        reply.setMaxWidth(CanvasLook.dp(268));
        reply.setText(said);
        bubble.addView(reply);

        if (round.hasSource()) {
            final StringBuilder where = new StringBuilder();
            if (!round.sourceChapter.trim().isEmpty()) {
                where.append(DebateSession.cut(round.sourceChapter, 28));
            }
            if (round.sourcePage >= 0) {
                where.append(where.length() > 0 ? " · " : "").append(a.getString(R.string.debate_source_page, round.sourcePage + 1));
            }
            if (round.quoted) {
                where.append(where.length() > 0 ? " · " : "").append(a.getString(R.string.debate_source_quoted));
            }
            final TextView source = new TextView(a);
            source.setText(where.toString());
            source.setTypeface(CanvasLook.mono(a, 400));
            source.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            source.setTextColor(CanvasLook.ink2());
            source.setGravity(Gravity.CENTER_VERTICAL);
            source.setSingleLine(true);
            source.setEllipsize(TextUtils.TruncateAt.END);
            source.setMaxWidth(CanvasLook.dp(268));
            source.setMinHeight(CanvasLook.dp(28));
            source.setPadding(CanvasLook.dp(8), 0, CanvasLook.dp(8), 0);
            source.setBackground(CanvasParts.ripple(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(6))));
            source.setContentDescription(where + ". " + a.getString(R.string.debate_source_go));
            source.setOnClickListener(v -> jump(round));
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = CanvasLook.dp(6);
            bubble.addView(source, lp);
        }
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CanvasLook.dp(8);
        row.addView(bubble, lp);
        return row;
    }

    /** "REFEREE · ROUND 2", then "Point to you: ... Next round: ...". */
    private View refereeCard(int n, DebateRound round) {
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.round(CanvasLook.bg(), CanvasLook.line(), CanvasLook.dp(CanvasLook.RADIUS_LARGE_DP)));
        final int p = CanvasLook.dp(12);
        card.setPadding(p, p, p, p);
        card.addView(mono(a.getString(R.string.debate_referee_round, n), CanvasLook.muted(), false));

        final String point = round.point == DebateReply.Point.READER ? a.getString(R.string.debate_point_you)
                : round.point == DebateReply.Point.AUTHOR ? a.getString(R.string.debate_point_author, whoMid())
                : a.getString(R.string.debate_point_draw);
        final SpannableStringBuilder says = new SpannableStringBuilder(point);
        says.setSpan(new StyleSpan(Typeface.BOLD), 0, says.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!round.why.isEmpty()) {
            says.append(" ").append(round.why);
        }
        if (!round.next.isEmpty()) {
            says.append(" ").append(a.getString(R.string.debate_next_round, round.next));
        }
        final TextView verdict = text("", 14, 400, CanvasLook.ink());
        verdict.setLineSpacing(0, 1.2f);
        verdict.setPadding(0, CanvasLook.dp(4), 0, 0);
        verdict.setText(says);
        card.addView(verdict);
        return card;
    }

    /** After the last round: the score, who is ahead, where the two really disagree. */
    private View closingCard(String who) {
        final DebateResult result = session.result(0);
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CanvasParts.accentCard());
        final int p = CanvasLook.dp(14);
        card.setPadding(p, p, p, p);
        card.addView(mono(a.getString(R.string.debate_final_label), CanvasLook.accent(), true));
        final TextView score = text(score(result, who), CanvasLook.TEXT_HEADING, 600, CanvasLook.ink());
        score.setPadding(0, CanvasLook.dp(4), 0, 0);
        card.addView(score);
        final DebateReply.Point leader = result.leader();
        final TextView won = text(leader == DebateReply.Point.READER ? a.getString(R.string.debate_won_you)
                : leader == DebateReply.Point.AUTHOR ? a.getString(R.string.debate_won_author, who)
                : a.getString(R.string.debate_won_draw), 14, 400, CanvasLook.ink2());
        won.setPadding(0, CanvasLook.dp(2), 0, 0);
        card.addView(won);
        if (!session.crux.isEmpty()) {
            card.addView(mono(a.getString(R.string.debate_crux_label), CanvasLook.accent(), true), spaced(14));
            final TextView crux = text(session.crux, 14, 400, CanvasLook.ink());
            crux.setLineSpacing(0, 1.2f);
            crux.setPadding(0, CanvasLook.dp(4), 0, 0);
            card.addView(crux);
        }

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView again = button(R.string.debate_again, false);
        again.setOnClickListener(v -> {
            final DebateSession played = session;
            session = new DebateSession(bookTitle, chapter.key, chapter.title, author, ideas, played.claim);
            input.setText(played.readerPosition);
            input.setSelection(input.getText().length());
            error = null;
            render();
        });
        final TextView done = button(R.string.debate_done, true);
        done.setOnClickListener(v -> dialog.dismiss());
        final LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        final LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, CanvasLook.dp(48), 1);
        right.leftMargin = CanvasLook.dp(8);
        buttons.addView(again, left);
        buttons.addView(done, right);
        card.addView(buttons, spaced(14));
        return card;
    }

    // --- parts ---

    private static LinearLayout.LayoutParams spaced(int topDp) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CanvasLook.dp(topDp);
        return lp;
    }

    /** A chat bubble: corner radii in dp, clockwise from the top left. */
    private static GradientDrawable bubble(int fill, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        final float tl = CanvasLook.dp(topLeft);
        final float tr = CanvasLook.dp(topRight);
        final float br = CanvasLook.dp(bottomRight);
        final float bl = CanvasLook.dp(bottomLeft);
        d.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return d;
    }

    private TextView text(String s, float sp, int weight, int color) {
        final TextView t = new TextView(a);
        t.setText(s);
        t.setTypeface(CanvasLook.ui(a, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView small(String s, int color) {
        final TextView t = text(s, CanvasLook.TEXT_SMALL, 400, color);
        t.setPadding(0, CanvasLook.dp(4), 0, 0);
        return t;
    }

    /** A small capital label in the mono face (the app's own words only, never a title). */
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

    private TextView button(int s, boolean primary) {
        final TextView t = text(a.getString(s), CanvasLook.TEXT_TITLE, 600, primary ? CanvasLook.onAccent() : CanvasLook.ink());
        t.setGravity(Gravity.CENTER);
        t.setPadding(CanvasLook.dp(12), 0, CanvasLook.dp(12), 0);
        t.setBackground(primary ? CanvasParts.primaryButton() : CanvasParts.secondaryButton());
        return t;
    }
}
