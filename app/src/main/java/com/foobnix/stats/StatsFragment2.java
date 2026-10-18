package com.foobnix.stats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.util.Pair;

import com.foobnix.android.utils.Dips;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.wrapper.UITab;
import com.foobnix.ui2.AppDB;
import com.foobnix.ui2.fragment.UIFragment;
import com.foobnix.webdav.SyncEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * The Statistics tab (F-007), a main-screen tab like Recent or Bookmarks. The head holds the
 * period (day ... all), the device and a menu (daily goal, clear); the scroll below holds the
 * sections Overview, Habits, Books and Library. Built in code from {@link StatsReport}; the
 * summaries are read from the logs in the background ({@link StatsLog#refresh}).
 */
public class StatsFragment2 extends UIFragment<Object> {

    public static final Pair<Integer, Integer> PAIR = new Pair<>(R.string.stats_tab, R.drawable.glyphicons_stats_bars);

    private static final int OVERVIEW = 0;
    private static final int HABITS = 1;
    private static final int BOOKS = 2;
    private static final int LIBRARY = 3;
    private static final int BY_MINUTES = 0;
    private static final int BY_WORDS = 1;
    private static final int BY_PAGES = 2;

    private StatsReport.Period period = StatsReport.Period.WEEK;
    private int section = OVERVIEW;
    private int barsBy = BY_MINUTES;
    /** null: every device. */
    private String device;

    private View topPanel;
    private TextView periodView;
    private TextView deviceView;
    private ScrollView scroll;
    private LinearLayout content;
    private Context c;
    private int accent;
    private int textSecondary;
    private boolean loaded;
    private List<StatsReport.LibBook> library;

    @Override
    public Pair<Integer, Integer> getNameAndIconRes() {
        return PAIR;
    }

    /**
     * Shows the tab once on a device that had a tab order saved before the tab existed (those
     * get new tabs hidden); hidden by the reader afterwards, it stays hidden.
     */
    public static void showTabOnce(Context context) {
        final SharedPreferences sp = context.getSharedPreferences("stats", Context.MODE_PRIVATE);
        if (!sp.getBoolean("tabShown", false)) {
            UITab.show(UITab.StatsFragment);
            sp.edit().putBoolean("tabShown", true).apply();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_stats2, container, false);
        c = getActivity();
        topPanel = view.findViewById(R.id.topPanel);
        periodView = view.findViewById(R.id.statsPeriod);
        deviceView = view.findViewById(R.id.statsDevice);
        scroll = view.findViewById(R.id.scroll);
        content = view.findViewById(R.id.statsContent);
        final android.content.res.TypedArray a = c.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        textSecondary = a.getColor(0, Color.GRAY);
        a.recycle();

        periodView.setOnClickListener(this::choosePeriod);
        deviceView.setOnClickListener(this::chooseDevice);
        view.findViewById(R.id.onSettings).setOnClickListener(this::menu);

        onTintChanged();
        updateHead();
        content.addView(text(getString(R.string.stats_loading), false));
        refresh();
        return view;
    }

    /** Pull to refresh asks this: true while the stats are scrolled down. */
    public boolean canScrollUp() {
        return scroll != null && scroll.canScrollVertically(-1);
    }

    @Override
    public void onTintChanged() {
        accent = TintUtil.color;
        if (topPanel != null) {
            TintUtil.setBackgroundFillColor(topPanel, TintUtil.color);
        }
        render();
    }

    @Override
    public void onTabShown(boolean selected) {
        super.onTabShown(selected);
        if (selected) {
            refresh();
        }
    }

    @Override
    public void notifyFragment() {
        refresh();
    }

    @Override
    public void resetFragment() {
        library = null;
        refresh();
    }

    private void refresh() {
        StatsLog.refresh(() -> {
            loaded = true;
            render();
        });
    }

    // ---------------------------------------------------------------- head

    private void updateHead() {
        final int[] names = {R.string.stats_day, R.string.stats_week, R.string.stats_month, R.string.stats_year, R.string.stats_all};
        periodView.setText(getString(names[period.ordinal()]) + " ▾");
        deviceView.setText((device == null ? getString(R.string.stats_all_devices) : deviceNames().getOrDefault(device, device)) + " ▾");
    }

    private void choosePeriod(View v) {
        final PopupMenu p = new PopupMenu(c, v);
        final int[] names = {R.string.stats_day, R.string.stats_week, R.string.stats_month, R.string.stats_year, R.string.stats_all};
        for (int i = 0; i < names.length; i++) {
            final StatsReport.Period chosen = StatsReport.Period.values()[i];
            p.getMenu().add(names[i]).setOnMenuItemClickListener(item -> {
                period = chosen;
                updateHead();
                render();
                return true;
            });
        }
        p.show();
    }

    private void chooseDevice(View v) {
        final PopupMenu p = new PopupMenu(c, v);
        p.getMenu().add(R.string.stats_all_devices).setOnMenuItemClickListener(item -> {
            device = null;
            updateHead();
            render();
            return true;
        });
        for (Map.Entry<String, String> e : deviceNames().entrySet()) {
            final String id = e.getKey();
            final String mine = id.equals(StatsLog.deviceId()) ? " •" : "";
            p.getMenu().add(e.getValue() + mine).setOnMenuItemClickListener(item -> {
                device = id;
                updateHead();
                render();
                return true;
            });
        }
        p.show();
    }

    private void menu(View v) {
        final PopupMenu p = new PopupMenu(c, v);
        p.getMenu().add(R.string.stats_goal_set).setOnMenuItemClickListener(item -> {
            setGoal();
            return true;
        });
        p.getMenu().add(R.string.stats_clear).setOnMenuItemClickListener(item -> {
            confirmClear();
            return true;
        });
        p.show();
    }

    private Map<String, String> deviceNames() {
        final StatsStore s = StatsLog.store();
        return s == null ? Collections.emptyMap() : s.devices();
    }

    private void confirmClear() {
        new AlertDialog.Builder(c)
                .setMessage(R.string.stats_clear_confirm)
                .setPositiveButton(R.string.stats_clear, (d, w) -> StatsLog.clear(() -> {
                    SyncEvents.statsWritten();
                    render();
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setGoal() {
        final EditText input = new EditText(c);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(AppState.get().readingGoalMinutes));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(c)
                .setTitle(R.string.stats_goal_set)
                .setMessage(R.string.stats_goal_minutes)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, w) -> {
                    try {
                        AppState.get().readingGoalMinutes = Math.max(0, Math.min(24 * 60, Integer.parseInt(input.getText().toString().trim())));
                    } catch (NumberFormatException e) {
                        AppState.get().readingGoalMinutes = 0;
                    }
                    AppProfile.save(c);
                    SyncEvents.settingsChanged();
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- sections

    private void render() {
        if (!loaded || content == null || !isAdded()) {
            return;
        }
        updateHead();
        content.removeAllViews();
        content.addView(sections());
        final StatsData d = StatsLog.data(device == null ? null : Collections.singleton(device));
        final int today = Days.today();
        if (d.days.isEmpty()) {
            content.addView(text(getString(R.string.stats_collecting), false));
            return;
        }
        final int[] range = period.range(today, d);
        final StatsReport.Summary s = StatsReport.summary(d, range[0], range[1]);
        switch (section) {
            case HABITS:
                habits(d, s, today);
                break;
            case BOOKS:
                books(d, s, range);
                break;
            case LIBRARY:
                library(d, range);
                break;
            default:
                overview(d, s, today);
        }
    }

    /** The row of sections at the top of the scroll. */
    private View sections() {
        final LinearLayout row = new LinearLayout(c);
        final int[] names = {R.string.stats_overview, R.string.stats_habits, R.string.stats_books, R.string.stats_library};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            row.addView(chip(getString(names[i]), t == section, v -> {
                section = t;
                render();
            }));
        }
        final android.widget.HorizontalScrollView s = new android.widget.HorizontalScrollView(c);
        s.setHorizontalScrollBarEnabled(false);
        s.addView(row);
        return s;
    }

    private void overview(StatsData d, StatsReport.Summary s, int today) {
        goal(d, today);

        final List<String[]> tiles = new ArrayList<>();
        tiles.add(new String[]{getString(R.string.stats_read), StatsFormat.duration(c, s.readMs)});
        tiles.add(new String[]{getString(R.string.stats_listened), StatsFormat.duration(c, s.listenMs)});
        tiles.add(new String[]{getString(R.string.stats_words), StatsFormat.number(s.words)});
        tiles.add(new String[]{getString(R.string.stats_pages), StatsFormat.decimal(s.pages)});
        if (s.wpm > 0 || s.cpm == 0) {
            tiles.add(new String[]{getString(R.string.stats_wpm), s.wpm > 0 ? String.valueOf(s.wpm) : "–"});
        }
        if (s.cpm > 0) {
            tiles.add(new String[]{getString(R.string.stats_cpm), String.valueOf(s.cpm)});
        }
        tiles.add(new String[]{getString(R.string.stats_sessions), String.valueOf(s.sessions)});
        if (s.eyeMs > 0) {
            tiles.add(new String[]{getString(R.string.stats_looking), Math.round(100f * s.lookMs / s.eyeMs) + "%"});
        }
        content.addView(tiles(tiles));

        final int[] previous = period.previous(today, d);
        if (previous != null && s.wpm > 0) {
            final int before = StatsReport.summary(d, previous[0], previous[1]).wpm;
            if (before > 0 && before != s.wpm) {
                final int change = Math.round(Math.abs(s.wpm - before) * 100f / before);
                final String what = getString(s.wpm > before ? R.string.stats_faster : R.string.stats_slower, change);
                content.addView(text(getString(R.string.stats_change, what), false));
            }
        }

        // bars of the period, by minutes, words or pages
        final StatsReport.Series series = StatsReport.series(d, period, today);
        final LinearLayout by = new LinearLayout(c);
        final int[] byNames = {R.string.stats_minutes, R.string.stats_words, R.string.stats_pages};
        for (int i = 0; i < byNames.length; i++) {
            final int b = i;
            by.addView(chip(getString(byNames[i]), b == barsBy, v -> {
                barsBy = b;
                render();
            }));
        }
        content.addView(by);
        final TextView caption = text(getString(R.string.stats_tap_bar), false);
        final float[] values = new float[series.labels.size()];
        final String[] valueTexts = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (barsBy == BY_WORDS) {
                values[i] = series.words.get(i);
                valueTexts[i] = StatsFormat.number(series.words.get(i));
            } else if (barsBy == BY_PAGES) {
                values[i] = series.pages.get(i);
                valueTexts[i] = StatsFormat.decimal(series.pages.get(i));
            } else {
                values[i] = series.ms.get(i) / 60_000f;
                valueTexts[i] = StatsFormat.duration(c, series.ms.get(i));
            }
        }
        final StatsCharts.Bars bars = new StatsCharts.Bars(c, accent);
        bars.set(values, series.labels.toArray(new String[0]), todayIndex(today),
                i -> caption.setText(series.labels.get(i) + ": " + valueTexts[i]));
        content.addView(bars);
        content.addView(caption);

        final int[] weekly = StatsReport.weeklyWpm(d, today, 26);
        if (count(weekly) >= 2) {
            content.addView(title(getString(R.string.stats_speed_trend)));
            final TextView trendCaption = text("", false);
            final StatsCharts.Line line = new StatsCharts.Line(c, accent);
            line.set(weekly, StatsFormat.date(Days.weekStart(today) - 7 * 25), StatsFormat.date(Days.weekStart(today)),
                    i -> trendCaption.setText(StatsFormat.date(Days.weekStart(today) - 7 * (25 - i)) + ": " + weekly[i] + " wpm"));
            content.addView(line);
            content.addView(trendCaption);
        }

        content.addView(text(getString(R.string.stats_projected_books, StatsReport.projectedBooksThisYear(d, today)), true));
    }

    /** Which bar of {@link StatsReport#series} is today, -1 when bars are months. */
    private int todayIndex(int today) {
        switch (period) {
            case DAY:
                return 6;
            case WEEK:
                return today - Days.weekStart(today);
            case MONTH:
                return today - Days.monthStart(today);
            default:
                return -1;
        }
    }

    private void goal(StatsData d, int today) {
        final LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        final StatsCharts.Ring ring = new StatsCharts.Ring(c, accent);
        final StatsReport.Goal g = StatsReport.goal(d, today, AppState.get().readingGoalMinutes);
        final LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(16), 0, 0, 0);
        texts.addView(title(getString(R.string.stats_goal)));
        if (g == null) {
            final int todayMinutes = StatsReport.minutes(StatsReport.summary(d, today, today).totalMs());
            ring.set(0, String.valueOf(todayMinutes), "min");
            texts.addView(text(getString(R.string.stats_tap_goal), false));
        } else {
            ring.set(g.todayMinutes / (float) g.goalMinutes, String.valueOf(g.todayMinutes), getString(R.string.stats_goal_of, g.goalMinutes));
            final String balance = StatsFormat.duration(c, Math.abs(g.weekBalance) * 60_000L);
            texts.addView(text(getString(g.weekBalance >= 0 ? R.string.stats_goal_ahead : R.string.stats_goal_behind, balance), false));
        }
        ring.setOnClickListener(v -> setGoal());
        texts.setOnClickListener(v -> setGoal());
        row.addView(ring);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(row);
    }

    private void habits(StatsData d, StatsReport.Summary s, int today) {
        final StatsReport.Streak streak = StatsReport.streak(d, today);
        content.addView(title(getString(R.string.stats_streak)));
        content.addView(text(getString(R.string.stats_streak_line, streak.current, streak.longest, streak.daysThisMonth), false));
        final StatsCharts.Calendar calendar = new StatsCharts.Calendar(c, accent);
        final int monthStart = Days.monthStart(today);
        calendar.set(streak.monthDays, StatsData.weekdayOf(monthStart), Days.monthEnd(today) - monthStart + 1,
                today - monthStart + 1, StatsFormat.weekdayLetters());
        content.addView(calendar);

        content.addView(title(getString(R.string.stats_heatmap)));
        final TextView heatCaption = text("", false);
        final String[] weekdays = StatsFormat.weekdayLetters();
        final StatsCharts.Heatmap heat = new StatsCharts.Heatmap(c, accent);
        heat.set(s.heatMs, weekdays, getString(R.string.stats_less), getString(R.string.stats_more), i -> heatCaption.setText(
                weekdays[i / 24] + " " + String.format(java.util.Locale.US, "%02d:00", i % 24) + ": " + StatsFormat.duration(c, s.heatMs[i / 24][i % 24])));
        content.addView(heat);
        content.addView(heatCaption);
        final int best = StatsReport.bestHour(s);
        if (best >= 0) {
            content.addView(text(getString(R.string.stats_best_window, best, (best + 1) % 24, s.hourSpeed[best].wpm()), true));
        }

        content.addView(title(getString(R.string.stats_sessions)));
        content.addView(text(getString(R.string.stats_sessions_line, StatsFormat.decimal(s.sessionsPerDay()),
                StatsFormat.duration(c, s.averageSessionMs()), StatsFormat.duration(c, s.longestSessionMs)), false));
        content.addView(text(getString(R.string.stats_bounces, s.bounces), false));

        final List<Integer> fatigue = StatsReport.fatigue(s);
        if (fatigue.size() >= 2) {
            content.addView(title(getString(R.string.stats_fatigue)));
            final int[] values = new int[fatigue.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = fatigue.get(i);
            }
            final TextView caption = text("", false);
            final StatsCharts.Line line = new StatsCharts.Line(c, accent);
            line.set(values, "0", (values.length * 10) + " min", i -> caption.setText((i * 10) + "–" + (i * 10 + 10) + " min: " + values[i] + " wpm"));
            content.addView(line);
            content.addView(caption);
        }

        final int[] parts = StatsReport.partsOfDay(s);
        final List<String> partLabels = new ArrayList<>();
        final List<Long> partValues = new ArrayList<>();
        final int[] partNames = {R.string.stats_night, R.string.stats_morning, R.string.stats_afternoon, R.string.stats_evening};
        for (int i = 0; i < 4; i++) {
            if (parts[i] > 0) {
                partLabels.add(getString(partNames[i]));
                partValues.add((long) parts[i]);
            }
        }
        hbars(R.string.stats_parts_of_day, partLabels, partValues, v -> v + " wpm");

        final List<String> langLabels = new ArrayList<>();
        final List<Long> langValues = new ArrayList<>();
        for (Map.Entry<String, StatsData.Speed> e : s.langSpeed.entrySet()) {
            if (e.getValue().ms >= 5 * 60_000L) {
                langLabels.add(e.getKey().isEmpty() ? "?" : e.getKey());
                langValues.add((long) e.getValue().wpm());
            }
        }
        if (langLabels.size() > 1) {
            hbars(R.string.stats_languages, langLabels, langValues, String::valueOf);
        }

        final Map<String, String> names = deviceNames();
        if (device == null && names.size() > 1) {
            final List<String> devLabels = new ArrayList<>();
            final List<Long> devValues = new ArrayList<>();
            for (Map.Entry<String, String> e : names.entrySet()) {
                final StatsData one = StatsLog.data(Collections.singleton(e.getKey()));
                devLabels.add(e.getValue());
                devValues.add(StatsReport.summary(one, s.from, s.to).totalMs());
            }
            hbars(R.string.stats_devices, devLabels, devValues, v -> StatsFormat.duration(c, v));
        }
    }

    private void books(StatsData d, StatsReport.Summary s, int[] range) {
        final long now = System.currentTimeMillis();
        final List<StatsData.Book> inProgress = StatsReport.books(d, b -> StatsReport.isInProgress(b, now));
        content.addView(text(getString(R.string.stats_in_progress, inProgress.size(), s.switches), true));
        content.addView(text(getString(R.string.stats_started_finished, s.starts, s.finishes), false));
        for (StatsData.Book b : inProgress) {
            content.addView(bookRow(b.displayTitle(), Math.round(b.progress * 100) + "% · " + StatsFormat.duration(c, b.totalMs())));
        }

        final int tz = TimeZone.getDefault().getOffset(now) / 60_000;
        final List<StatsReport.Finish> finished = StatsReport.finishes(d, range[0], range[1], tz);
        if (!finished.isEmpty()) {
            content.addView(title(getString(R.string.stats_finished)));
            for (StatsReport.Finish f : finished) {
                content.addView(bookRow(f.book.displayTitle(), getString(R.string.stats_finished_row, f.days, StatsFormat.duration(c, f.ms))));
            }
        }

        final List<StatsData.Book> stopped = StatsReport.books(d, b -> StatsReport.isStopped(b, now));
        if (!stopped.isEmpty()) {
            content.addView(title(getString(R.string.stats_stopped)));
            for (StatsData.Book b : stopped) {
                content.addView(bookRow(b.displayTitle(), getString(R.string.stats_stopped_row, Math.round(b.progress * 100),
                        (int) ((now - b.lastOpen) / 86_400_000L))));
            }
        }

        content.addView(title(getString(R.string.stats_books)));
        content.addView(text(getString(R.string.stats_engagement, s.highlights, s.notes, StatsFormat.decimal(s.per1000(s.highlights + s.notes))), false));
        content.addView(text(getString(R.string.stats_actions, s.bookmarks, s.searches, s.lookups), false));
        content.addView(text(getString(R.string.stats_difficulty, StatsFormat.decimal(s.per1000(s.lookups)),
                StatsFormat.decimal(s.per1000(s.backtracks + s.rereads)), s.skims), false));

        final List<String> labels = new ArrayList<>();
        final List<String> rows = new ArrayList<>();
        for (StatsData.Book b : d.books.values()) {
            final double flesch = WordCounter.flesch(c, b.key);
            if (!Double.isNaN(flesch) && b.speed.ms >= 10 * 60_000L) {
                labels.add(b.displayTitle());
                rows.add(getString(R.string.stats_readability_row, (int) Math.round(flesch), b.wpm.median()));
            }
        }
        if (!labels.isEmpty()) {
            content.addView(title(getString(R.string.stats_readability)));
            for (int i = 0; i < labels.size(); i++) {
                content.addView(bookRow(labels.get(i), rows.get(i)));
            }
        }
    }

    private void library(final StatsData d, final int[] range) {
        if (library == null) {
            content.addView(text(getString(R.string.stats_loading), false));
            final Activity activity = getActivity();
            new Thread(() -> {
                final List<StatsReport.LibBook> list = new ArrayList<>();
                for (FileMeta m : AppDB.get().getAll()) {
                    if (!Boolean.TRUE.equals(m.getIsSearchBook()) || m.getPath() == null) {
                        continue;
                    }
                    list.add(new StatsReport.LibBook(ExtUtils.getFileName(m.getPath()), m.getAuthor(), m.getGenre(), m.getLang(),
                            m.getPages() == null ? 0 : m.getPages(), m.getDate() == null ? 0 : m.getDate(),
                            m.getIsRecentProgress() == null ? 0 : m.getIsRecentProgress()));
                }
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        library = list;
                        render();
                    });
                }
            }, "@T stats library").start();
            return;
        }
        final long now = System.currentTimeMillis();
        final StatsReport.Library l = StatsReport.library(d, library, range[0], range[1], now, TimeZone.getDefault().getOffset(now) / 60_000);
        content.addView(text(getString(R.string.stats_backlog, l.backlog), true));
        content.addView(text(getString(R.string.stats_acquired, l.acquired, l.finished), false));
        content.addView(text(getString(R.string.stats_average_length, l.finishedPages, l.stoppedPages), false));
        breakdown(R.string.stats_by_genre, l.byGenre);
        breakdown(R.string.stats_by_author, l.byAuthor);
        breakdown(R.string.stats_by_language, l.byLanguage);
        breakdown(R.string.stats_by_length, l.byLength);
    }

    private void breakdown(int title, Map<String, Long> values) {
        final List<String> labels = new ArrayList<>();
        final List<Long> ms = new ArrayList<>();
        for (Map.Entry<String, Long> e : values.entrySet()) {
            if (labels.size() == 8) {
                break;
            }
            labels.add(e.getKey());
            ms.add(e.getValue());
        }
        hbars(title, labels, ms, v -> StatsFormat.duration(c, v));
    }

    // ---------------------------------------------------------------- views

    private interface ValueText {
        String of(long value);
    }

    private void hbars(int title, List<String> labels, List<Long> values, ValueText format) {
        if (labels.isEmpty()) {
            return;
        }
        content.addView(title(getString(title)));
        final long[] v = new long[values.size()];
        final List<String> texts = new ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            v[i] = values.get(i);
            texts.add(format.of(v[i]));
        }
        final StatsCharts.HBars bars = new StatsCharts.HBars(c, accent);
        bars.set(labels, v, texts);
        content.addView(bars);
    }

    private TextView chip(String s, boolean selected, View.OnClickListener click) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setTextColor(selected ? accent : textSecondary);
        t.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setOnClickListener(click);
        return t;
    }

    private View tiles(List<String[]> tiles) {
        final LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                grid.addView(row);
            }
            final LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(0, dp(6), dp(8), dp(6));
            final TextView value = new TextView(c);
            value.setText(tiles.get(i)[1]);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            value.setTypeface(Typeface.DEFAULT_BOLD);
            tile.addView(value);
            tile.addView(text(tiles.get(i)[0], false));
            row.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        if (tiles.size() % 2 == 1 && row != null) {
            row.addView(new View(c), new LinearLayout.LayoutParams(0, 0, 1));
        }
        return grid;
    }

    private TextView title(String s) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private TextView text(String s, boolean primary) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (!primary) {
            t.setTextColor(textSecondary);
        }
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private View bookRow(String title, String detail) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        final TextView t = text(title, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t);
        row.addView(text(detail, false));
        return row;
    }

    private static int count(int[] values) {
        int n = 0;
        for (int v : values) {
            if (v > 0) {
                n++;
            }
        }
        return n;
    }

    private static int dp(int v) {
        return Dips.dpToPx(v);
    }
}
