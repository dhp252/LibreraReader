package com.foobnix.annotation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.foobnix.ai.AiCategory;
import com.foobnix.ai.AiStore;
import com.foobnix.ai.BookChapters;
import com.foobnix.ai.BookText;
import com.foobnix.ai.QuoteMatcher;
import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.ResultResponse2;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppBookmark;
import com.foobnix.pdf.info.BookmarksData;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.AnnotationType;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.view.MyProgressDialog;
import com.foobnix.pdf.info.widget.ChooserDialogFragment;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.tts.TTSHighlight;
import com.foobnix.ui2.AppDB;

import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.AnnotationLook;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Export notes (Markdown)" (D-62, D-63): a book's marks with their notes, the annotations already
 * inside its PDF, its bookmarks and AI key passages, as Markdown ({@link MarkdownExport}), worked
 * out in the background on a copy of the book laid out like the reader; then shared, or saved as
 * {@code <Title>.md} in a folder picked once on this device.
 */
public final class MarksExport {

    private static final String PREFS = "marks_export";
    private static final String FOLDER = "folder";

    private static final class Result {
        String title = "";
        String fileName = "notes.md";
        String markdown = "";
        boolean empty;
    }

    private MarksExport() {
    }

    /**
     * @param dc the reader of the book when it is open (its layout gives the page numbers), else null
     */
    public static void start(final Activity a, final String bookPath, final DocumentController dc) {
        if (bookPath == null) {
            return;
        }
        final boolean open = dc != null && dc.getCurrentBook() != null && bookPath.equals(dc.getCurrentBook().getPath());
        final int width = open && dc.getBookWidth() > 0 ? dc.getBookWidth() : Dips.screenWidth();
        final int height = open && dc.getBookHeight() > 0 ? dc.getBookHeight() : Dips.screenHeight();
        final int fontSize = (int) BookCSS.get().fontSizeSp;
        final MyProgressDialog progress = MyProgressDialog.show(a, a.getString(R.string.marks_exporting));
        progress.show();
        new Thread(() -> {
            Result result = null;
            Exception error = null;
            try {
                result = build(a.getApplicationContext(), bookPath, width, height, fontSize);
            } catch (Exception e) {
                LOG.e(e);
                error = e;
            }
            final Result done = result;
            final Exception failed = error;
            a.runOnUiThread(() -> {
                progress.dismiss();
                if (failed != null || done == null) {
                    Toast.makeText(a, a.getString(R.string.marks_export_failed, failed == null ? "" : String.valueOf(failed.getMessage())),
                            Toast.LENGTH_LONG).show();
                } else if (done.empty) {
                    Toast.makeText(a, R.string.marks_export_nothing, Toast.LENGTH_LONG).show();
                } else {
                    choose(a, done);
                }
            });
        }, "@T marks export").start();
    }

    private static Result build(Context c, String path, int width, int height, int fontSize) {
        final Result result = new Result();
        final String file = new File(path).getName();
        String title = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        String author = "";
        try {
            final FileMeta meta = AppDB.get().load(path);
            if (meta != null) {
                if (meta.getTitle() != null && !meta.getTitle().trim().isEmpty()) {
                    title = meta.getTitle().trim();
                }
                author = meta.getAuthor() == null ? "" : meta.getAuthor().trim();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        result.title = title;
        result.fileName = safeName(title) + ".md";

        final String bookName = MarksLayer.bookName(path);
        final List<MarkdownExport.Item> items = new ArrayList<>();
        final List<MarkdownExport.Chapter> chapters = new ArrayList<>();
        final List<MarkdownExport.Bookmark> bookmarks = new ArrayList<>();
        final List<MarkdownExport.Passage> passages = new ArrayList<>();

        final BookText text = BookText.open(path, width, height, fontSize);
        try {
            final int pageCount = text == null ? 0 : text.pageCount();
            if (text != null) {
                for (BookChapters.Entry entry : text.toc()) {
                    if (entry.page >= 0) {
                        chapters.add(new MarkdownExport.Chapter(entry.title, entry.page + 1));
                    }
                }
            }

            // the reader's marks
            final Map<Integer, QuoteMatcher.Words> wordsOfPage = new HashMap<>();
            final TextAnchor.Pages pages = page -> {
                QuoteMatcher.Words words = wordsOfPage.get(page);
                if (words == null && text != null) {
                    words = new QuoteMatcher.Words(BookText.words(text.text(page)));
                    wordsOfPage.put(page, words);
                }
                return words;
            };
            for (Mark mark : MarksStore.get().load(bookName).values()) {
                if (mark.isInk()) {
                    continue;
                }
                int page = mark.firstPage();
                float order = mark.firstTop();
                if (mark.anchor != null) {
                    final String key = TextAnchor.key(mark.quote);
                    final int found = TextAnchor.findPage(mark.anchor, key, pageCount, pages);
                    page = found >= 0 ? found : TextAnchor.expectedPage(mark.anchor, pageCount);
                    // where on the page: the place of its first word among the page's words
                    final QuoteMatcher.Words words = found >= 0 ? pages.words(found) : null;
                    final int[] range = words == null ? null : TextAnchor.find(words, key, mark.anchor);
                    order = range == null || words.size() == 0 ? 1 : (float) range[0] / words.size();
                }
                items.add(new MarkdownExport.Item(page + 1, order, mark.quote, mark.note, mark.color, kindWord(mark.kind)));
            }

            // annotations already inside the PDF (other apps, older Librera)
            if (text != null && path.toLowerCase(Locale.US).endsWith(".pdf")) {
                for (int page = 0; page < pageCount; page++) {
                    final BookText.PageAnnotations inside = text.annotations(page);
                    for (int i = 0; i < inside.annotations.size(); i++) {
                        final MarkdownExport.Item item = embedded(inside, i, page);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }

            // bookmarks
            for (AppBookmark bookmark : BookmarksData.get().getBookmarksByBook(path)) {
                final int page = pageCount > 0 ? bookmark.getPage(pageCount) : Math.max(1, Math.round(bookmark.getPercent() * 100));
                bookmarks.add(new MarkdownExport.Bookmark(page, bookmark.getText()));
            }

            // AI key passages
            for (AiStore.Chapter chapter : AiStore.get().load(bookName).highlights) {
                for (AiStore.Quote quote : chapter.quotes) {
                    passages.add(new MarkdownExport.Passage(chapter.title, quote.page + 1, quote.text,
                            ColorNames.emoji(ColorNames.hex(quote.category.color)) + " " + categoryName(c, quote.category)));
                }
            }
        } finally {
            if (text != null) {
                text.close();
            }
        }

        final String outline = studyOutline(bookName, title, items);
        result.empty = MarkdownExport.isEmpty(items, bookmarks, passages) && outline.isEmpty();
        final String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        result.markdown = MarkdownExport.build(new MarkdownExport.Book(title, author, file, date, chapters), items, bookmarks,
                passages, outline);
        return result;
    }

    /**
     * The outline of a studied book (F-009) as Markdown, with the reader's notes under the chapters
     * they are in; empty for a book not studied.
     */
    private static String studyOutline(String bookName, String title, List<MarkdownExport.Item> items) {
        final com.foobnix.study.StudyBook study = com.foobnix.study.StudyStore.get().load(bookName);
        if (!study.hasPreview()) {
            return "";
        }
        final Map<String, List<com.foobnix.study.StudyBook.Node>> notes = new HashMap<>();
        for (MarkdownExport.Item item : items) {
            if (item.note == null || item.note.trim().isEmpty()) {
                continue;
            }
            for (com.foobnix.study.StudyBook.Chapter c : study.chapters.values()) {
                if (item.page - 1 >= c.from && item.page - 1 < c.to) {
                    final com.foobnix.study.StudyBook.Node note = new com.foobnix.study.StudyBook.Node(
                            item.note.replace("\r", "").replace("\n", " ").trim(), com.foobnix.study.Outline.NOTE, item.quote);
                    note.page = item.page - 1;
                    List<com.foobnix.study.StudyBook.Node> list = notes.get(c.key);
                    if (list == null) {
                        list = new ArrayList<>();
                        notes.put(c.key, list);
                    }
                    list.add(note);
                    break;
                }
            }
        }
        return com.foobnix.study.Outline.toMarkdown(com.foobnix.study.Outline.build(title, study, notes));
    }

    /** An annotation inside the PDF as an export item: its words, comment and color; null for drawings, links... */
    private static MarkdownExport.Item embedded(BookText.PageAnnotations inside, int index, int page) {
        final Annotation annotation = inside.annotations.get(index);
        final AnnotationType type = annotation.type;
        final String kind;
        if (type == AnnotationType.HIGHLIGHT) {
            kind = "highlight";
        } else if (type == AnnotationType.UNDERLINE || type == AnnotationType.SQUIGGLY) {
            kind = "underline";
        } else if (type == AnnotationType.STRIKEOUT) {
            kind = "strikethrough";
        } else if (type == AnnotationType.TEXT || type == AnnotationType.FREETEXT) {
            kind = "note";
        } else {
            return null;
        }
        final AnnotationLook look = index < inside.looks.size() ? inside.looks.get(index) : null;
        final List<RectF> lines = new ArrayList<>();
        if (look != null && !look.lines.isEmpty()) {
            lines.addAll(look.lines);
        } else if (!"note".equals(kind)) {
            lines.add(annotation);
        }
        final List<String> words = new ArrayList<>();
        for (TextWord word : TTSHighlight.words(inside.text)) {
            for (RectF line : lines) {
                if (line.contains(word.centerX(), word.centerY())) {
                    words.add(word.getWord());
                    break;
                }
            }
        }
        final String quote = QuoteMatcher.join(words);
        final String note = annotation.text == null ? "" : annotation.text.trim();
        if (quote.isEmpty() && note.isEmpty()) {
            return null;
        }
        final String color = look != null && look.color != 0 ? ColorNames.hex(look.color) : ColorNames.PALETTE[0];
        return new MarkdownExport.Item(page + 1, annotation.top, quote, note, color, kind);
    }

    private static String kindWord(String kind) {
        if (Mark.UNDERLINE.equals(kind)) {
            return "underline";
        }
        if (Mark.STRIKE.equals(kind)) {
            return "strikethrough";
        }
        return "highlight";
    }

    private static String categoryName(Context c, AiCategory category) {
        try {
            return c.getString(com.foobnix.ai.AiLegend.nameOf(category));
        } catch (Exception e) {
            return category.code;
        }
    }

    /** A file name from a title: no characters a file system refuses, not too long. */
    static String safeName(String title) {
        String name = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isEmpty()) {
            name = "notes";
        }
        return name.length() > 120 ? name.substring(0, 120).trim() : name;
    }

    // ---- where it goes ----

    private static void choose(final Activity a, final Result result) {
        final String folder = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(FOLDER, "");
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        items.add(a.getString(R.string.marks_export_share));
        actions.add(() -> share(a, result));
        if (a instanceof FragmentActivity) {
            if (!folder.isEmpty() && new File(folder).isDirectory()) {
                items.add(a.getString(R.string.marks_export_save_to, new File(folder).getName()));
                actions.add(() -> save(a, result, new File(folder)));
                items.add(a.getString(R.string.marks_export_other_folder));
            } else {
                items.add(a.getString(R.string.marks_export_choose_folder));
            }
            actions.add(() -> pickFolder((FragmentActivity) a, folder, result));
        }
        new AlertDialog.Builder(a)
                .setTitle(result.fileName)
                .setItems(items.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void pickFolder(final FragmentActivity a, String start, final Result result) {
        ChooserDialogFragment.chooseFolder(a, start.isEmpty() ? null : start).setOnSelectListener(new ResultResponse2<String, Dialog>() {
            @Override
            public boolean onResultRecive(String path, Dialog dialog) {
                dialog.dismiss();
                a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(FOLDER, path).apply();
                save(a, result, new File(path));
                return false;
            }
        });
    }

    private static void save(Activity a, Result result, File folder) {
        final File out = new File(folder, result.fileName);
        try {
            write(out, result.markdown);
            Toast.makeText(a, a.getString(R.string.marks_export_saved, out.getPath()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            LOG.e(e);
            Toast.makeText(a, a.getString(R.string.marks_export_failed, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
        }
    }

    /** As a .md file and as text: a notes app takes the text, mail or Drive the file. */
    private static void share(Activity a, Result result) {
        try {
            final File folder = new File(a.getCacheDir(), "export");
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new java.io.IOException("cannot create " + folder);
            }
            final File out = new File(folder, result.fileName);
            write(out, result.markdown);
            final Uri uri = FileProvider.getUriForFile(a, Apps.getPackageName(a) + ".provider", out);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, result.title);
            intent.putExtra(Intent.EXTRA_TEXT, result.markdown);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri(result.fileName, uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(intent, a.getString(R.string.share)));
        } catch (Exception e) {
            LOG.e(e);
            Toast.makeText(a, a.getString(R.string.marks_export_failed, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
        }
    }

    private static void write(File file, String text) throws java.io.IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
