package com.foobnix.stats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foobnix.android.utils.Dips;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.ui2.AppDB;
import com.foobnix.webdav.SyncEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * The Reading Stats screen (F-007): a period (day ... all), the devices, and four tabs:
 * overview, habits, books, library. Built in code from {@link StatsReport}; the summaries are
 * read from the logs in the background ({@link StatsLog#refresh}).
 */
public class ReadingStatsActivity extends Activity {

    private static final int OVERVIEW = 0;
    private static final int HABITS = 1;
    private static final int BOOKS = 2;
    private static final int LIBRARY = 3;
    private static final int BY_MINUTES = 0;
    private static final int BY_WORDS = 1;
    private static final int BY_PAGES = 2;

    private StatsReport.Period period = StatsReport.Period.WEEK;
    private int tab = OVERVIEW;
    private int barsBy = BY_MINUTES;
    /** null: every device. */
    private String device;

    private LinearLayout periods;
    private LinearLayout tabs;
    private TextView devices;
    private LinearLayout content;
    private int accent;
    private int textSecondary;
    private boolean loaded;
    private List<StatsReport.LibBook> library;

    public static void open(Context c) {
        c.startActivity(new Intent(c, ReadingStatsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (AppState.get().appTheme == AppState.THEME_LIGHT || AppState.get().appTheme == AppState.THEME_INK) {
            setTheme(R.style.StyledIndicatorsWhite);
        } else {
            setTheme(R.style.StyledIndicatorsBlack);
        }
        super.onCreate(savedInstanceState);
        accent = TintUtil.color;
        final android.content.res.TypedArray a = getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        textSecondary = a.getColor(0, Color.GRAY);
        a.recycle();

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header());

        periods = row();
        tabs = row();
        root.addView(scrollable(periods));
        root.addView(scrollable(tabs));

        final ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(root);
        // white clock and icons over the accent header
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), root).setAppearanceLightStatusBars(false);
        // drawn edge to edge from Android 15 on (targetSdk 36): the header runs under the status
        // bar, everything else keeps clear of the bars
        final View header = root.getChildAt(0);
        final int headerTop = header.getPaddingTop();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            final androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top, header.getPaddingRight(), header.getPaddingBottom());
            return insets;
        });

        buildChips();
        content.addView(text(getString(R.string.stats_loading), false));
        StatsLog.refresh(() -> {
            loaded = true;
            render();
        });
    }

    // ---------------------------------------------------------------- chrome

    private View header() {
        final LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(accent);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));

        final TextView back = headerButton("←");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        final TextView title = new TextView(this);
        title.setText(R.string.stats_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        devices = headerButton("");
        devices.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        devices.setOnClickListener(this::chooseDevice);
        bar.addView(devices);

        final TextView menu = headerButton("⋮");
        menu.setOnClickListener(v -> {
            final PopupMenu p = new PopupMenu(this, v);
            p.getMenu().add(R.string.stats_clear).setOnMenuItemClickListener(item -> {
                confirmClear();
                return true;
            });
            p.show();
        });
        bar.addView(menu);
        return bar;
    }

    private TextView headerButton(String s) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout row() {
        final LinearLayout r = new LinearLayout(this);
        r.setPadding(dp(8), dp(2), dp(8), dp(2));
        return r;
    }

    private View scrollable(View v) {
        final HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.addView(v);
        return s;
    }

    private void buildChips() {
        periods.removeAllViews();
        final int[] names = {R.string.stats_day, R.string.stats_week, R.string.stats_month, R.string.stats_year, R.string.stats_all};
        for (int i = 0; i < names.length; i++) {
            final StatsReport.Period p = StatsReport.Period.values()[i];
            periods.addView(chip(getString(names[i]), p == period, v -> {
                period = p;
                buildChips();
                render();
            }));
        }
        tabs.removeAllViews();
        final int[] tabNames = {R.string.stats_overview, R.string.stats_habits, R.string.stats_books, R.string.stats_library};
        for (int i = 0; i < tabNames.length; i++) {
            final int t = i;
            tabs.addView(chip(getString(tabNames[i]), t == tab, v -> {
                tab = t;
                buildChips();
                render();
            }));
        }
        final Map<String, String> names2 = deviceNames();
        devices.setText(device == null ? getString(R.string.stats_all_devices) : names2.getOrDefault(device, device));
    }

    private TextView chip(String s, boolean selected, View.OnClickListener click) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setTextColor(selected ? accent : textSecondary);
        t.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setOnClickListener(click);
        return t;
    }

    private Map<String, String> deviceNames() {
        final StatsStore s = StatsLog.store();
        return s == null ? Collections.emptyMap() : s.devices();
    }

    private void chooseDevice(View v) {
        final PopupMenu p = new PopupMenu(this, v);
        p.getMenu().add(R.string.stats_all_devices).setOnMenuItemClickListener(item -> {
            device = null;
            buildChips();
            render();
            return true;
        });
        for (Map.Entry<String, String> e : deviceNames().entrySet()) {
            final String id = e.getKey();
            final String mine = id.equals(StatsLog.deviceId()) ? " •" : "";
            p.getMenu().add(e.getValue() + mine).setOnMenuItemClickListener(item -> {
                device = id;
                buildChips();
                render();
                return true;
            });
        }
        p.show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.stats_clear_confirm)
                .setPositiveButton(R.string.stats_clear, (d, w) -> StatsLog.clear(() -> {
                    SyncEvents.statsWritten();
                    render();
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- tabs

    private void render() {
        if (!loaded || isFinishing()) {
            return;
        }
        content.removeAllViews();
        final StatsData d = StatsLog.data(device == null ? null : Collections.singleton(device));
        final int today = Days.today();
        if (d.days.isEmpty()) {
            content.addView(text(getString(R.string.stats_collecting), false));
            return;
        }
        final int[] range = period.range(today, d);
        final StatsReport.Summary s = StatsReport.summary(d, range[0], range[1]);
        switch (tab) {
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

    private void overview(StatsData d, StatsReport.Summary s, int today) {
        goal(d, today);

        final List<String[]> tiles = new ArrayList<>();
        tiles.add(new String[]{getString(R.string.stats_read), StatsFormat.duration(this, s.readMs)});
        tiles.add(new String[]{getString(R.string.stats_listened), StatsFormat.duration(this, s.listenMs)});
        tiles.add(new String[]{getString(R.string.stats_words), StatsFormat.number(s.words)});
        tiles.add(new String[]{getString(R.string.stats_pages), StatsFormat.decimal(s.pages)});
        if (s.wpm > 0 || s.cpm == 0) {
            tiles.add(new String[]{getString(R.string.stats_wpm), s.wpm > 0 ? String.valueOf(s.wpm) : "–"});
        }
        if (s.cpm > 0) {
            tiles.add(new String[]{getString(R.string.stats_cpm), String.valueOf(s.cpm)});
        }
        tiles.add(new String[]{getString(R.string.stats_sessions), String.valueOf(s.sessions)});
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
        final LinearLayout by = new LinearLayout(this);
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
                valueTexts[i] = StatsFormat.duration(this, series.ms.get(i));
            }
        }
        final int highlight = period == StatsReport.Period.DAY || period == StatsReport.Period.WEEK || period == StatsReport.Period.MONTH
                ? todayIndex(today) : -1;
        final StatsCharts.Bars bars = new StatsCharts.Bars(this, accent);
        bars.set(values, series.labels.toArray(new String[0]), highlight,
                i -> caption.setText(series.labels.get(i) + ": " + valueTexts[i]));
        content.addView(bars);
        content.addView(caption);

        final int[] weekly = StatsReport.weeklyWpm(d, today, 26);
        if (count(weekly) >= 2) {
            content.addView(title(getString(R.string.stats_speed_trend)));
            final TextView trendCaption = text("", false);
            final StatsCharts.Line line = new StatsCharts.Line(this, accent);
            line.set(weekly, StatsFormat.date(Days.weekStart(today) - 7 * 25), StatsFormat.date(Days.weekStart(today)),
                    i -> trendCaption.setText(StatsFormat.date(Days.weekStart(today) - 7 * (25 - i)) + ": " + weekly[i] + " wpm"));
            content.addView(line);
            content.addView(trendCaption);
        }

        content.addView(text(getString(R.string.stats_projected_books, StatsReport.projectedBooksThisYear(d, today)), true));
    }

    /** Which bar of {@link StatsReport#series} is today. */
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
        final LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        final StatsCharts.Ring ring = new StatsCharts.Ring(this, accent);
        final StatsReport.Goal g = StatsReport.goal(d, today, AppState.get().readingGoalMinutes);
        final LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(16), 0, 0, 0);
        texts.addView(title(getString(R.string.stats_goal)));
        if (g == null) {
            final int todayMinutes = StatsReport.minutes(StatsReport.summary(d, today, today).totalMs());
            ring.set(0, String.valueOf(todayMinutes), "min");
            texts.addView(text(getString(R.string.stats_tap_goal), false));
        } else {
            ring.set(g.todayMinutes / (float) g.goalMinutes, String.valueOf(g.todayMinutes), getString(R.string.stats_goal_of, g.goalMinutes));
            final String balance = StatsFormat.duration(this, Math.abs(g.weekBalance) * 60_000L);
            texts.addView(text(getString(g.weekBalance >= 0 ? R.string.stats_goal_ahead : R.string.stats_goal_behind, balance), false));
        }
        ring.setOnClickListener(v -> setGoal());
        texts.setOnClickListener(v -> setGoal());
        row.addView(ring);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(row);
    }

    private void setGoal() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(AppState.get().readingGoalMinutes));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.stats_goal_set)
                .setMessage(R.string.stats_goal_minutes)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, w) -> {
                    try {
                        AppState.get().readingGoalMinutes = Math.max(0, Math.min(24 * 60, Integer.parseInt(input.getText().toString().trim())));
                    } catch (NumberFormatException e) {
                        AppState.get().readingGoalMinutes = 0;
                    }
                    AppProfile.save(this);
                    SyncEvents.settingsChanged();
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void habits(StatsData d, StatsReport.Summary s, int today) {
        final StatsReport.Streak streak = StatsReport.streak(d, today);
        content.addView(title(getString(R.string.stats_streak)));
        content.addView(text(getString(R.string.stats_streak_line, streak.current, streak.longest, streak.daysThisMonth), false));
        final StatsCharts.Calendar calendar = new StatsCharts.Calendar(this, accent);
        final int monthStart = Days.monthStart(today);
        calendar.set(streak.monthDays, StatsData.weekdayOf(monthStart), Days.monthEnd(today) - monthStart + 1,
                today - monthStart + 1, StatsFormat.weekdayLetters());
        content.addView(calendar);

        content.addView(title(getString(R.string.stats_heatmap)));
        final TextView heatCaption = text("", false);
        final String[] weekdays = StatsFormat.weekdayLetters();
        final StatsCharts.Heatmap heat = new StatsCharts.Heatmap(this, accent);
        heat.set(s.heatMs, weekdays, getString(R.string.stats_less), getString(R.string.stats_more), i -> heatCaption.setText(
                weekdays[i / 24] + " " + String.format(java.util.Locale.US, "%02d:00", i % 24) + ": " + StatsFormat.duration(this, s.heatMs[i / 24][i % 24])));
        content.addView(heat);
        content.addView(heatCaption);
        final int best = StatsReport.bestHour(s);
        if (best >= 0) {
            content.addView(text(getString(R.string.stats_best_window, best, (best + 1) % 24, s.hourSpeed[best].wpm()), true));
        }

        content.addView(title(getString(R.string.stats_sessions)));
        content.addView(text(getString(R.string.stats_sessions_line, StatsFormat.decimal(s.sessionsPerDay()),
                StatsFormat.duration(this, s.averageSessionMs()), StatsFormat.duration(this, s.longestSessionMs)), false));
        content.addView(text(getString(R.string.stats_bounces, s.bounces), false));

        final List<Integer> fatigue = StatsReport.fatigue(s);
        if (fatigue.size() >= 2) {
            content.addView(title(getString(R.string.stats_fatigue)));
            final int[] values = new int[fatigue.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = fatigue.get(i);
            }
            final TextView caption = text("", false);
            final StatsCharts.Line line = new StatsCharts.Line(this, accent);
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
            hbars(R.string.stats_languages, langLabels, langValues, v -> String.valueOf(v));
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
            hbars(R.string.stats_devices, devLabels, devValues, v -> StatsFormat.duration(this, v));
        }
    }

    private void books(StatsData d, StatsReport.Summary s, int[] range) {
        final long now = System.currentTimeMillis();
        final List<StatsData.Book> inProgress = StatsReport.books(d, b -> StatsReport.isInProgress(b, now));
        content.addView(text(getString(R.string.stats_in_progress, inProgress.size(), s.switches), true));
        content.addView(text(getString(R.string.stats_started_finished, s.starts, s.finishes), false));
        for (StatsData.Book b : inProgress) {
            content.addView(bookRow(b.displayTitle(), Math.round(b.progress * 100) + "% · " + StatsFormat.duration(this, b.totalMs())));
        }

        final int tz = TimeZone.getDefault().getOffset(now) / 60_000;
        final List<StatsReport.Finish> finished = StatsReport.finishes(d, range[0], range[1], tz);
        if (!finished.isEmpty()) {
            content.addView(title(getString(R.string.stats_finished)));
            for (StatsReport.Finish f : finished) {
                content.addView(bookRow(f.book.displayTitle(), getString(R.string.stats_finished_row, f.days, StatsFormat.duration(this, f.ms))));
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
            final double flesch = WordCounter.flesch(this, b.key);
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
                runOnUiThread(() -> {
                    library = list;
                    render();
                });
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
        hbars(title, labels, ms, v -> StatsFormat.duration(this, v));
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
        final StatsCharts.HBars bars = new StatsCharts.HBars(this, accent);
        bars.set(labels, v, texts);
        content.addView(bars);
    }

    private View tiles(List<String[]> tiles) {
        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                grid.addView(row);
            }
            final LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(0, dp(6), dp(8), dp(6));
            final TextView value = new TextView(this);
            value.setText(tiles.get(i)[1]);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            value.setTypeface(Typeface.DEFAULT_BOLD);
            tile.addView(value);
            tile.addView(text(tiles.get(i)[0], false));
            row.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        if (tiles.size() % 2 == 1 && row != null) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        }
        return grid;
    }

    private TextView title(String s) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private TextView text(String s, boolean primary) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (!primary) {
            t.setTextColor(textSecondary);
        }
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private View bookRow(String title, String detail) {
        final LinearLayout row = new LinearLayout(this);
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

    private int dp(int v) {
        return Dips.dpToPx(v);
    }

    /** The Reading stats section of the preferences: today's line and the link to the screen. */
    public static void bindPreferences(final Activity a, final View root) {
        final View header = root.findViewById(R.id.statsHeader);
        if (header == null) {
            return;
        }
        TintUtil.setSectionFillColor(header, TintUtil.color);
        root.findViewById(R.id.statsPrefOpen).setOnClickListener(v -> open(a));
        final TextView status = root.findViewById(R.id.statsPrefStatus);
        StatsLog.refresh(() -> {
            final String line = status(a);
            status.setText(line);
            status.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    /** One line of reading for the settings: today's time and the streak. */
    public static String status(Context c) {
        final StatsData d = ReadingSpeed.data();
        if (d == null) {
            return "";
        }
        final int today = Days.today();
        return c.getString(R.string.stats_today_status, StatsFormat.duration(c, StatsReport.summary(d, today, today).totalMs()),
                StatsReport.streak(d, today).current);
    }
}
