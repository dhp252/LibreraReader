package com.foobnix.assist.edition;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.LOG;
import com.foobnix.canvas.CanvasLook;
import com.foobnix.pdf.info.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Export PDF: the edition on A5-like pages, drawn with the platform's {@link PdfDocument} and
 * {@link StaticLayout} (no library): a cover with the contents, then the same sections, chapters
 * and blocks as on screen, the book's title and "Your edition" with the page number at the head
 * of every page. Always in the light colours, whatever the app's theme. What goes where is
 * {@link EditionPrint}'s; this only measures the paragraphs and draws the slices. The file goes
 * to the cache's export folder (as the Markdown export of marks) and to the system's share sheet.
 */
final class EditionPdf {

    // the day colours of the canvas, fixed: a printed page is never dark
    private static final int INK = CanvasLook.INK;
    private static final int INK_2 = CanvasLook.INK_2;
    private static final int MUTED = CanvasLook.MUTED;
    private static final int ACCENT = CanvasLook.ACCENT;
    private static final int WARN = CanvasLook.SENSOR;
    private static final int RULE = CanvasLook.LINE;

    private static boolean busy;

    private EditionPdf() {
    }

    /** Writes the PDF off the main thread and hands it to the share sheet. */
    static void export(Activity a, EditionData data) {
        if (busy || data == null || data.model.isEmpty()) {
            return;
        }
        busy = true;
        Toast.makeText(a, R.string.canvas_t26_pdf_making, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File file = null;
            String error = null;
            try {
                final File folder = new File(a.getCacheDir(), "export");
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    throw new IOException("cannot create " + folder);
                }
                file = new File(folder, EditionPrint.fileName(data.model.bookTitle, data.words.yourEdition));
                write(a.getApplicationContext(), data, file);
            } catch (Throwable e) {
                LOG.e(e);
                error = String.valueOf(e.getMessage());
            }
            final File out = file;
            final String failed = error;
            a.runOnUiThread(() -> {
                busy = false;
                if (Apps.isDestroyedActivity(a)) {
                    return;
                }
                if (failed != null) {
                    Toast.makeText(a, a.getString(R.string.canvas_t26_pdf_failed, failed), Toast.LENGTH_LONG).show();
                } else {
                    share(a, out, data.model.bookTitle);
                }
            });
        }, "@T Edition PDF").start();
    }

    private static void share(Activity a, File file, String title) {
        try {
            final Uri uri = FileProvider.getUriForFile(a, Apps.getPackageName(a) + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri(file.getName(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(intent, a.getString(R.string.share)));
        } catch (Exception e) {
            LOG.e(e);
            Toast.makeText(a, a.getString(R.string.canvas_t26_pdf_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ the document

    private static void write(Context c, EditionData data, File file) throws IOException {
        final EditionModel m = data.model;
        final List<EditionPrint.Run> runs = EditionPrint.runs(m);
        final List<StaticLayout> layouts = new ArrayList<>();
        final List<EditionPrint.Piece> pieces = new ArrayList<>();
        for (EditionPrint.Run run : runs) {
            final StaticLayout layout = layout(run.text, paint(c, run.style, run.tone), run.width(EditionPrint.textWidth()),
                    run.style.lineHeight);
            layouts.add(layout);
            pieces.add(EditionPrint.Piece.of(run, layout.getLineCount()));
        }
        final List<List<EditionPrint.Slice>> pages = EditionPrint.layout(pieces, EditionPrint.textHeight());
        // the cover is page 1; where each section starts, for its contents
        final int[] sectionPage = new int[m.sections.size()];
        int section = 0;
        for (int p = 0; p < pages.size(); p++) {
            for (EditionPrint.Slice s : pages.get(p)) {
                if (runs.get(s.piece).style == EditionPrint.Style.SECTION && s.from == 0 && section < sectionPage.length) {
                    sectionPage[section++] = p + 2;
                }
            }
        }
        final String head = EditionPrint.head(m, data.words);
        final PdfDocument doc = new PdfDocument();
        try {
            final int total = pages.size() + 1;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(EditionPrint.PAGE_WIDTH,
                    EditionPrint.PAGE_HEIGHT, 1).create());
            cover(c, page.getCanvas(), data, total, sectionPage);
            doc.finishPage(page);
            for (int p = 0; p < pages.size(); p++) {
                page = doc.startPage(new PdfDocument.PageInfo.Builder(EditionPrint.PAGE_WIDTH, EditionPrint.PAGE_HEIGHT,
                        p + 2).create());
                final Canvas canvas = page.getCanvas();
                head(c, canvas, head, p + 2);
                for (EditionPrint.Slice s : pages.get(p)) {
                    slice(canvas, runs.get(s.piece), layouts.get(s.piece), s);
                }
                doc.finishPage(page);
            }
            try (OutputStream out = new FileOutputStream(file)) {
                doc.writeTo(out);
            }
        } finally {
            doc.close();
        }
    }

    /** The lines of a slice, clipped out of its paragraph's layout, and the bar beside a marked paragraph. */
    private static void slice(Canvas canvas, EditionPrint.Run run, StaticLayout layout, EditionPrint.Slice s) {
        final float x = EditionPrint.MARGIN_X + (run.bar ? EditionPrint.BAR_INDENT : 0);
        final float y = EditionPrint.MARGIN_TOP + s.top;
        final int top = layout.getLineTop(s.from);
        final int bottom = layout.getLineBottom(s.to - 1);
        canvas.save();
        canvas.translate(x, y - top);
        canvas.clipRect(0, top, layout.getWidth(), bottom);
        layout.draw(canvas);
        canvas.restore();
        if (run.bar) {
            final Paint bar = new Paint();
            bar.setColor(run.tone == EditionPrint.Tone.WARN ? WARN : ACCENT);
            canvas.drawRect(EditionPrint.MARGIN_X, y + 1, EditionPrint.MARGIN_X + 2,
                    y + (s.to - s.from) * run.style.lineHeight - 3, bar);
        }
    }

    /** The running head: the book's title and "YOUR EDITION" on the left, the page number on the right. */
    private static void head(Context c, Canvas canvas, String head, int number) {
        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(CanvasLook.mono(c, 500));
        paint.setTextSize(6.5f);
        paint.setLetterSpacing(0.05f);
        paint.setColor(MUTED);
        final String n = String.valueOf(number);
        final float right = EditionPrint.PAGE_WIDTH - EditionPrint.MARGIN_X;
        final float numberWidth = paint.measureText(n);
        canvas.drawText(n, right - numberWidth, EditionPrint.HEAD_Y, paint);
        final CharSequence title = TextUtils.ellipsize(head, paint, EditionPrint.textWidth() - numberWidth - 16,
                TextUtils.TruncateAt.END);
        canvas.drawText(title, 0, title.length(), EditionPrint.MARGIN_X, EditionPrint.HEAD_Y, paint);
        final Paint rule = new Paint();
        rule.setColor(RULE);
        canvas.drawRect(EditionPrint.MARGIN_X, EditionPrint.HEAD_Y + 7, right, EditionPrint.HEAD_Y + 7.5f, rule);
    }

    /** Page 1: "YOUR EDITION", the title, what it is made of, and the contents with their pages. */
    private static void cover(Context c, Canvas canvas, EditionData data, int totalPages, int[] sectionPage) {
        final EditionModel m = data.model;
        final float width = EditionPrint.textWidth();
        float y = 120;
        y = draw(canvas, layout(data.words.yourEdition.toUpperCase(java.util.Locale.ROOT),
                paint(c, EditionPrint.Style.LABEL, EditionPrint.Tone.ACCENT), width, 10), y) + 10;
        final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        title.setTypeface(EditionLook.serif(c, 0));
        title.setTextSize(24);
        title.setColor(INK);
        y = draw(canvas, layout(m.bookTitle, title, width, 30), y) + 8;
        final TextPaint soft = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        soft.setTypeface(CanvasLook.ui(c, 400));
        soft.setTextSize(11);
        soft.setColor(INK_2);
        y = draw(canvas, layout(c.getResources().getQuantityString(R.plurals.canvas_t26_in_pages, totalPages, totalPages),
                soft, width, 15), y) + 14;
        final String from = c.getResources().getQuantityString(R.plurals.canvas_t26_from_chapters, m.chapters, m.chapters);
        final String made = m.kinds.isEmpty() ? from
                : c.getString(R.string.canvas_t26_from_your, from, TextUtils.join(", ", m.kinds));
        y = draw(canvas, layout(made.toUpperCase(java.util.Locale.ROOT), paint(c, EditionPrint.Style.LABEL,
                EditionPrint.Tone.NONE), width, 10), y) + 40;

        y = draw(canvas, layout(c.getString(R.string.canvas_t26_contents).toUpperCase(java.util.Locale.ROOT),
                paint(c, EditionPrint.Style.LABEL, EditionPrint.Tone.NONE), width, 10), y) + 8;
        final TextPaint row = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        row.setTypeface(CanvasLook.ui(c, 400));
        row.setTextSize(10.5f);
        row.setColor(INK);
        final TextPaint number = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        number.setTypeface(CanvasLook.mono(c, 400));
        number.setTextSize(8.5f);
        number.setColor(MUTED);
        final Paint rule = new Paint();
        rule.setColor(RULE);
        final float right = EditionPrint.PAGE_WIDTH - EditionPrint.MARGIN_X;
        for (int i = 0; i < m.sections.size(); i++) {
            final float base = y + 13;
            canvas.drawText(String.valueOf(i + 1), EditionPrint.MARGIN_X, base, number);
            final String pageNumber = sectionPage[i] > 0 ? String.valueOf(sectionPage[i]) : "";
            final float numberWidth = number.measureText(pageNumber);
            final CharSequence name = TextUtils.ellipsize(m.sections.get(i).title, row, width - 18 - numberWidth - 12,
                    TextUtils.TruncateAt.END);
            canvas.drawText(name, 0, name.length(), EditionPrint.MARGIN_X + 18, base, row);
            canvas.drawText(pageNumber, right - numberWidth, base, number);
            y += 22;
            canvas.drawRect(EditionPrint.MARGIN_X, y - 1, right, y - 0.5f, rule);
        }
    }

    /** Draws a layout at the left margin, {@code y} down the page; returns where it ends. */
    private static float draw(Canvas canvas, StaticLayout layout, float y) {
        canvas.save();
        canvas.translate(EditionPrint.MARGIN_X, y);
        layout.draw(canvas);
        canvas.restore();
        return y + layout.getHeight();
    }

    // ------------------------------------------------------------------ type

    /** A paragraph's lines at a width, {@code lineHeight} points apart. */
    private static StaticLayout layout(CharSequence text, TextPaint paint, float width, float lineHeight) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(lineHeight - paint.getFontSpacing(), 1f)
                .build();
    }

    /** A style's face, size and colour: the reading face for the reader's words, the interface's for the rest. */
    private static TextPaint paint(Context c, EditionPrint.Style style, EditionPrint.Tone tone) {
        final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(style.size);
        p.setColor(INK);
        final Typeface face;
        switch (style) {
            case SECTION:
                face = CanvasLook.ui(c, 600);
                p.setColor(ACCENT);
                break;
            case CHAPTER:
                face = EditionLook.serif(c, 2);
                break;
            case LABEL:
                face = CanvasLook.mono(c, 500);
                p.setLetterSpacing(0.05f);
                p.setColor(tone == EditionPrint.Tone.WARN ? WARN : tone == EditionPrint.Tone.ACCENT ? ACCENT : MUTED);
                break;
            case TITLE:
                face = CanvasLook.ui(c, 600);
                break;
            case LINE:
            case BODY:
                face = EditionLook.serif(c, 0);
                break;
            case QUESTION:
                face = EditionLook.serif(c, 1);
                break;
            case SMALL:
                face = CanvasLook.ui(c, 400);
                p.setColor(MUTED);
                break;
            default:
                face = CanvasLook.ui(c, 400);
                break;
        }
        p.setTypeface(face);
        return p;
    }
}
