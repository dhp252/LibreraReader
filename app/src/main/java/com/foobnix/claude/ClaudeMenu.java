package com.foobnix.claude;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import com.foobnix.ai.AiHighlights;
import com.foobnix.android.utils.Apps;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.view.MyPopupMenu;
import com.foobnix.pdf.info.wrapper.DocumentController;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ✦ menu's row for an outside Claude (shown while Work with Claude through WebDAV is on): how
 * many answers wait for the book, and a box with the desk's state and "Prepare now".
 */
public final class ClaudeMenu {

    private ClaudeMenu() {
    }

    /** Adds the row to the ✦ menu. */
    public static void add(Activity a, DocumentController dc, MyPopupMenu menu) {
        if (!Claude.isOn(a) || dc == null || dc.getCurrentBook() == null) {
            return;
        }
        final String book = AiHighlights.bookName(dc.getCurrentBook().getPath());
        final int waiting = count(Claude.desk(book), "");
        final String title = waiting > 0 ? a.getString(R.string.claude_menu_waiting, waiting) : a.getString(R.string.claude_menu);
        menu.getMenu().add(title).setIcon(R.drawable.ic_ai_sparkle).setOnMenuItemClickListener(item -> {
            show(a, dc, book);
            return true;
        });
    }

    private static void show(Activity a, DocumentController dc, String book) {
        final File desk = Claude.desk(book);
        final StringBuilder text = new StringBuilder();
        final File json = new File(desk, ClaudeDesk.BOOK_JSON);
        text.append(json.isFile()
                ? a.getString(R.string.claude_desk_ready, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT).format(new java.util.Date(json.lastModified())))
                : a.getString(R.string.claude_desk_none));
        final Map<String, Integer> byFeature = waitingByFeature(desk);
        text.append("\n\n");
        if (byFeature.isEmpty()) {
            text.append(a.getString(R.string.claude_none_waiting));
        } else {
            text.append(a.getString(R.string.claude_waiting_title));
            for (Map.Entry<String, Integer> e : byFeature.entrySet()) {
                text.append("\n• ").append(e.getKey()).append(": ").append(e.getValue());
            }
            text.append("\n").append(a.getString(R.string.claude_waiting_when));
        }
        final int used = count(desk, ClaudeAnswers.USED);
        final int rejected = count(desk, ClaudeAnswers.REJECTED);
        if (used + rejected > 0) {
            text.append("\n\n").append(a.getString(R.string.claude_used_rejected, used, rejected));
        }
        text.append("\n\n").append(a.getString(R.string.claude_where, "profile/" + ClaudeAnswers.FOLDER + "/"
                + ClaudeAnswers.BOOKS + "/" + desk.getName()));
        final AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(R.string.claude_menu)
                .setMessage(text.toString())
                .setPositiveButton(R.string.claude_prepare, (dialog, w) -> {
                    Toast.makeText(a, R.string.claude_preparing, Toast.LENGTH_SHORT).show();
                    ClaudeBook.prepareNow(a, dc, () -> a.runOnUiThread(() -> {
                        if (!Apps.isDestroyedActivity(a)) {
                            Toast.makeText(a, R.string.claude_prepared, Toast.LENGTH_LONG).show();
                        }
                    }));
                })
                .setNegativeButton(R.string.close, null)
                .show();
        if (com.foobnix.canvas.CanvasLook.isOn()) {
            com.foobnix.canvas.dialogs.CanvasDialogs.styleBuilt(d);
        }
    }

    private static Map<String, Integer> waitingByFeature(File desk) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        for (ClaudeFeature f : ClaudeFeature.values()) {
            final List<String> chapters = ClaudeAnswers.chapters(desk, f.id, f.format);
            if (!chapters.isEmpty()) {
                out.put(f.id, chapters.size());
            }
        }
        return out;
    }

    /** Answer files in answers/ (sub ""), answers/used or answers/rejected. */
    private static int count(File desk, String sub) {
        final File[] files = new File(new File(desk, ClaudeAnswers.ANSWERS), sub).listFiles();
        int n = 0;
        for (int i = 0; files != null && i < files.length; i++) {
            if (files[i].isFile() && files[i].getName().endsWith(".json")) {
                n++;
            }
        }
        return n;
    }
}
