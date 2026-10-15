package com.foobnix.pdf.search.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.Intents;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.Safe;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.CopyAsyncTask;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.PageUrl;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.AnnotationType;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.model.OutlineLinkWrapper;
import com.foobnix.pdf.info.view.MyProgressDialog;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.search.activity.msg.InvalidateMessage;
import com.foobnix.pdf.search.activity.msg.MessageAutoFit;
import com.foobnix.pdf.search.activity.msg.MessageCenterHorizontally;
import com.foobnix.pdf.search.activity.msg.MessagePageXY;
import com.foobnix.pdf.search.activity.msg.MovePageAction;
import com.foobnix.sys.Colors;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.sys.TempHolder;
import com.foobnix.tts.TTSEngine;
import com.foobnix.tts.TTSNotification;
import com.foobnix.ui2.AppDB;
import com.foobnix.ui2.FileMetaCore;

import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.core.PageSearcher;
import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.droids.mupdf.codec.MuPdfLinks;
import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class HorizontalModeController extends DocumentController {

    int currentPage;
    String bookPath;
    CodecDocument codeDocument;
    int imageWidth, imageHeight;
    private int pagesCount;
    private CopyAsyncTask searchTask;
    private boolean isTextFormat = false;
    private SharedPreferences matrixSP;
    private volatile boolean isClosed = false;

    @Override public CodecDocument getCodecDocument() {
        return codeDocument;
    }

    @Override public boolean hasPDFAnnotations() {
        return false;
    }

    public HorizontalModeController(Activity activity, int w, int h) {
        super(activity);

        isTextFormat = ExtUtils.isTextFomat(activity.getIntent());
        udpateImageSize(isTextFormat, w, h);

        matrixSP = activity.getSharedPreferences("matrix", Context.MODE_PRIVATE);

        PageImageState.get()
                      .cleanSelectedWords();
        PageImageState.get().pagesText.clear();
        // Keep crop rectangles in lockstep with the text they are used to map.
        PageImageState.get().clearPagesCrop();

        AppSP.get().isSmartReflow = false;

        if (isTextFormat) {
            AppSP.get().isCrop = false;
            AppSP.get().isCut = false;
            AppSP.get().isLocked = true;
        }

        bookPath = Apps.getBookPathFromActivity(activity);
        setCurrentBook(new File(bookPath));

        AppBook bs = SettingsManager.getBookSettings(bookPath);

        if (bs != null) {
            LOG.d("isRTL", "AppBook.rtl", bs.rtl);
            AppSP.get().isRTL = bs.rtl;
            AppSP.get().isCut = bs.sp;
            AppSP.get().isCrop = bs.cp;
            AppSP.get().isDouble = bs.dp;
            AppSP.get().isDoubleCoverAlone = bs.dc;
            AppSP.get().isLocked = bs.getLock(isTextFormat, AppState.get().lockBooksByDefault);
            TempHolder.get().pageDelta = bs.d;

            if (AppState.get().isCropPDF && !isTextFormat) {
                AppSP.get().isCrop = true;
            }
        }

        if (AppState.get().alwaysTwoPages) {
            AppSP.get().isDouble = true;
            AppSP.get().isCut = false;
            AppSP.get().isDoubleCoverAlone = false;
            AppSP.get().isSmartReflow = false;
        }

        FileMetaCore.checkOrCreateMetaInfo(activity);
        BookCSS.get()
               .detectLang(bookPath);

        String pasw = activity.getIntent()
                              .getStringExtra(EXTRA_PASSWORD);
        pasw = TxtUtils.nullToEmpty(pasw);

        if (AppSP.get().isDouble && isTextFormat) {
            imageWidth = Dips.screenWidth() / 2;
        }

        codeDocument = ImageExtractor.getNewCodecContext(bookPath, pasw, imageWidth, imageHeight);
        if (codeDocument != null) {
            pagesCount = codeDocument.getPageCount(imageWidth, imageHeight, BookCSS.get().fontSizeSp);
        } else {
            pagesCount = 0;
        }

        if (pagesCount <= 0) {
            CacheZipUtils.emptyAllCacheDirs();
            throw new IllegalArgumentException("Pages count: "+pagesCount);
        }

        try {
            FileMeta meta = AppDB.get()
                                 .load(bookPath);
            if (meta != null) {
                meta.setPages(pagesCount);
                AppDB.get()
                     .update(meta);
                LOG.d("update openDocument.getPageCount()", bookPath, pagesCount);
            }

        } catch (Exception e) {
            LOG.e(e);
        }

        AppDB.get()
             .addRecent(bookPath);

        float percent = Intents.getFloatAndClear(activity.getIntent(), DocumentController.EXTRA_PERCENT);

        if (percent > 0.0f) {
            currentPage = getBookmarkPage(percent, activity.getIntent().getStringExtra(EXTRA_BOOKMARK_TEXT),
                                          activity.getIntent().getStringExtra(EXTRA_BOOKMARK_PAGE_TEXT)) - 1;
        } else if (pagesCount > 0) {
            currentPage = findBookmarkPage(bs.getCurrentPage(getPageCount()).viewIndex + 1, null, bs.pt) - 1;
        }
        if (AppState.get().isAlwaysOpenOnPage1) {
            currentPage = 0;
        }

        if (false) {
            PageImageState.get().needAutoFit = true;
        } else {
            if (TxtUtils.isNotEmpty(bookPath) && !ExtUtils.isTextFomat(bookPath)) {
                String string = matrixSP.getString(bookPath.hashCode() + "", "");
                LOG.d("MATRIX", "READ STR", string);
                if (TxtUtils.isEmpty(string) || AppSP.get().isCut || AppSP.get().isCrop) {
                    PageImageState.get().needAutoFit = true;
                } else {
                    PageImageState.get().needAutoFit = false;
                }
                Matrix matrix = PageImageState.fromString(string);
                PageImageState.get()
                              .getMatrix()
                              .set(matrix);

                LOG.d("MATRIX", "READ", bookPath.hashCode() + "", PageImageState.get()
                                                                                .getMatrixAsString());

            }
        }

    }

    public static String getTempTitle(Activity a) {
        try {
            return getTitle(Apps.getBookPathFromActivity(a));
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    public static String getTitle(String path) {
        if (ExtUtils.hasTitle(path)) {
            return AppDB.get()
                        .getOrCreate(path)
                        .getTitle();
        }
        return new File(path).getName();
    }

    @Override public void updateRendering() {

    }

    @Override public void onScrollYPercent(float value) {
        int page2 = Math.round(value * getPageCount());
        onGoToPage(page2);
    }

    public void udpateImageSize(boolean isTextFormat, int w, int h) {
        LOG.d("udpateImageSize", w, h, isTextFormat);
        imageWidth = isTextFormat ? w :
                (int) (Math.min(Dips.screenWidth(), Dips.screenHeight()) * AppState.get().pageQuality);
        imageHeight = isTextFormat ? h :
                (int) (Math.max(Dips.screenWidth(), Dips.screenHeight()) * AppState.get().pageQuality);
    }

    @Override public int getBookHeight() {
        return imageHeight;
    }

    @Override public int getBookWidth() {
        return imageWidth;
    }

    @Override public void onLinkHistory() {
        if (!getLinkHistory().isEmpty()) {
            final int last = getLinkHistory().removeLast();
            onGoToPage(last);
        }
    }

    @Override public float getOffsetY() {
        return getCurentPageFirst1();
    }

    @Override public void cleanImageMatrix() {
        try {
            PageImageState.get()
                          .getMatrix()
                          .reset();
            matrixSP.edit()
                    .remove("" + bookPath.hashCode())
                    .commit();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    @Override public void saveAnnotationsToFile() {
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        currentPage = page;
    }

    @Override public int getCurentPageFirst1() {
        return currentPage + 1;
    }

    public int getOpenPageNumber() {
        return currentPage;
    }

    @Override public PageUrl getPageUrl(int page) {
        PageUrl build = PageUrl.build(getBookPath(), page, imageWidth, imageHeight);
        build.setDoText(true);
        return build;
    }

    public abstract void onGoToPageImpl(int page);

    public abstract void notifyAdapterDataChanged();

    public abstract void showInterstialAndClose();

    @Override public void onGoToPage(int page) {
        if (page <= getPageCount()) {
            onGoToPageImpl(page - 1);
        }
    }

    @Override public void onSrollLeft() {
        throw new RuntimeException("Not Implemented");
    }

    public TextWord[][] getPageText(int number) {
        LOG.d("Get page text for page", number);
        try {
            CodecPage page = codeDocument.getPage(number);
            if (!page.isRecycled()) {
                TextWord[][] text = page.getText();
                return text;
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return null;
    }

    @Override public synchronized String getTextForPage(int page) {
        try {
            CodecPage codecPage = codeDocument.getPage(page);
            if (!codecPage.isRecycled()) {
                String pageHTML = codecPage.getPageHTML();
                codecPage.recycle();
                pageHTML = TxtUtils.replaceHTMLforTTS(pageHTML);
                pageHTML = pageHTML.replace(TxtUtils.TTS_PAUSE, " ");
                pageHTML = pageHTML.replace(TxtUtils.NON_BREAKE_SPACE, " ");
                return pageHTML;

            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return "";
    }

    @Override public String getPageHtml() {
        try {
            CodecPage codecPage = codeDocument.getPage(getCurentPageFirst1() - 1);
            if (!codecPage.isRecycled()) {
                String pageHTML = codecPage.getPageHTML();
                pageHTML = TxtUtils.replaceHTMLforTTS(pageHTML);
                pageHTML = pageHTML.replace(TxtUtils.TTS_PAUSE, TxtUtils.TTS_PAUSE_VIEW);

                return pageHTML;

            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return "";
    }

    @Override public List<PageLink> getLinksForPage(int page) {
        try {
            return codeDocument.getPage(page)
                               .getPageLinks();
        } catch (Exception e) {
            LOG.e(e);
            return Collections.emptyList();
        }
    }

    @Override public void onSrollRight() {
        throw new RuntimeException("Not Implemented");

    }

    @Override public void onNextPage(boolean animate) {
        throw new RuntimeException("Not Implemented");

    }

    @Override public void onPrevPage(boolean animate) {
        throw new RuntimeException("Not Implemented");
    }

    @Override public void onNextScreen(boolean animate) {
        // TODO Auto-generated method stub

    }

    @Override public boolean isCropCurrentBook() {
        return false;
    }

    @Override public void onPrevScreen(boolean animate) {
        throw new RuntimeException("Not Implemented");

    }

    @Override public void onZoomInc() {
        EventBus.getDefault()
                .post(new MovePageAction(MovePageAction.ZOOM_PLUS, getCurentPage()));
    }

    @Override public void onZoomDec() {
        EventBus.getDefault()
                .post(new MovePageAction(MovePageAction.ZOOM_MINUS, getCurentPage()));
    }

    @Override public void onZoomInOut(int x, int y) {

    }

    @Override public String getFootNote(String text, String chapter) {
        try {
            return TxtUtils.getFooterNote(text, chapter, codeDocument.getFootNotes());
        } catch (Exception e) {
            LOG.e(e);
            return "";
        }
    }

    @Override public List<String> getMediaAttachments() {
        try {
            return codeDocument.getMediaAttachments();
        } catch (Exception e) {
            LOG.e(e);
            return Collections.emptyList();
        }
    }

    @Override public void onScrollDown() {
    }

    @Override public void onScrollUp() {
    }

    @Override public void onCloseActivityFinal(final Runnable run) {
        stopTimer();
        TTSEngine.get()
                 .stop();
        TTSNotification.hideNotification();

        Safe.run(new Runnable() {

            @Override public void run() {
                isClosed = true;
                // annotation changes still running must not meet a recycled document
                annotationExecutor.shutdown();
                try {
                    annotationExecutor.awaitTermination(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    LOG.e(e);
                }
                if (codeDocument != null) {
                    // closes that did not pass saveAnnotationsBeforeClose (closing the whole app)
                    if (!annotationsDecided && AppState.get().isSaveAnnotatationsAutomatically
                            && !codeDocument.isRecycled() && codeDocument.hasChanges()) {
                        codeDocument.saveAnnotations(bookPath);
                    }
                    codeDocument.recycle();
                    codeDocument = null;
                }
                try {
                    if (!ExtUtils.isTextFomat(bookPath)) {
                        matrixSP.edit()
                                .putString(bookPath.hashCode() + "", PageImageState.get()
                                                                                   .getMatrixAsString())
                                .commit();
                        LOG.d("MATRIX", "SAVE", bookPath.hashCode() + "", PageImageState.get()
                                                                                        .getMatrixAsString());
                    }
                } catch (Exception e) {
                    LOG.e(e);
                }

                //saveCurrentPage();
                LOG.d("_PAGE", "SAVE", getCurentPage());
                final Intent i = new Intent();
                i.putExtra("page", getCurentPage());
                activity.setResult(Activity.RESULT_OK, i);
                activity.finish();

                ImageExtractor.clearCodeDocument();
                if (run != null) {
                    run.run();
                }
            }
        });
    }

    @Override public void onCloseActivityAdnShowInterstial() {
        showInterstialAndClose();

    }

    @Override public void onNightMode() {
    }

    @Override public void onCrop() {
        throw new RuntimeException("Not Implemented");

    }

    @Override public void onFullScreen() {
        throw new RuntimeException("Not Implemented");

    }

    @Override public int getCurentPage() {
        LOG.d("_PAGE", "getCurentPage", currentPage);
        return currentPage;
    }

    @Override public int getPageCount() {
        return PageUrl.realToFake(pagesCount);
    }

    @Override public void onScrollY(int value) {
        throw new RuntimeException("Not Implemented");

    }

    @Override public void onAutoScroll() {
        throw new RuntimeException("Not Implemented");

    }

    @Override public void clearSelectedText() {
        EventBus.getDefault()
                .post(new MessagePageXY(MessagePageXY.TYPE_HIDE));
        AppState.get().selectedText = null;
        PageImageState.get()
                      .cleanSelectedWords();
        EventBus.getDefault()
                .post(new InvalidateMessage());
    }

    /*
     * PDF annotations in the paged view. A change goes to a page object opened for it alone: the
     * renderer opens, draws and recycles the shared cached page on its own threads, and the
     * annotations it keeps (PageImageState) hold handles of pages it already recycled. The native
     * page is reference counted, so a second object for it is safe; after the change the page is
     * drawn again (it is reopened for every render, so the change shows). Saving happens on close.
     */

    private final ExecutorService annotationExecutor = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "@T annotations");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean annotationsDecided;

    /**
     * View points of the current page (the activity knows its views) to points on the page
     * relative to its size, 0..1; null when the view cannot be mapped (two pages per screen, cut).
     */
    public List<PointF> toPagePoints(List<PointF> viewPoints) {
        return null;
    }

    /** The annotations of a page changed: the activity draws it again. */
    public void onPageAnnotationsChanged(int page) {
    }

    private interface PageChange {
        /** @return true when an annotation was created */
        boolean apply(CodecPage page);
    }

    private void changePage(final int page, final PageChange change) {
        final int group = annotationGroup();
        annotationExecutor.execute(() -> {
            try {
                if (codeDocument == null || codeDocument.isRecycled()) {
                    return;
                }
                final CodecPage codecPage = codeDocument.getPageInner(page);
                if (codecPage == null) {
                    return;
                }
                final boolean created;
                try {
                    created = change.apply(codecPage);
                } finally {
                    codecPage.recycle();
                }
                if (created) {
                    final CodecPage reopened = codeDocument.getPageInner(page);
                    if (reopened != null) {
                        recordCreatedAnnotation(page, reopened.getAnnotationsImpl().size(), group);
                        reopened.recycle();
                    }
                }
                activity.runOnUiThread(() -> onPageAnnotationsChanged(page));
            } catch (Exception e) {
                LOG.e(e);
            }
        });
    }

    /** Ink: {@code points} are DrawView points over the current page. */
    @Override public void saveChanges(List<PointF> points, final int color) {
        final List<PointF> normalized = toPagePoints(points);
        if (codeDocument == null || normalized == null || normalized.size() < 2) {
            return;
        }
        final int page = currentPage;
        final float width = AppState.get().editLineWidth * 3;
        final float alpha = AppState.get().editAlphaColor;
        changePage(page, codecPage -> {
            final CodecPageInfo info = codeDocument.getPageInfo(page);
            if (info == null) {
                return false;
            }
            // page units, as VerticalModeController.saveChanges passes them
            final PointF[][] arcs = new PointF[1][normalized.size()];
            for (int i = 0; i < normalized.size(); i++) {
                arcs[0][i] = new PointF(normalized.get(i).x * info.width, normalized.get(i).y * info.height);
            }
            codecPage.addAnnotation(Colors.toMupdfColor(color), arcs, width, alpha);
            return true;
        });
    }

    /** @param page 1-based, as Annotation.getPage() */
    @Override public void deleteAnnotation(long pageHander, final int page, final int index) {
        changePage(page - 1, codecPage -> {
            final List<Annotation> annotations = codecPage.getAnnotationsImpl();
            if (index >= 0 && index < annotations.size()) {
                codeDocument.deleteAnnotation(annotations.get(index).getPageHandler(), index);
            }
            return false;
        });
    }

    @Override public boolean undoLastAnnotation() {
        final List<int[]> group = popCreatedGroup();
        if (group.isEmpty() || codeDocument == null) {
            return false;
        }
        // newest first, one after the other on the annotation thread: each finds its page as it
        // was right after it was made
        for (final int[] last : group) {
            changePage(last[0], codecPage -> {
                final List<Annotation> annotations = codecPage.getAnnotationsImpl();
                // the newest annotation is only ours while the page still has the count it had after it
                if (annotations.size() == last[1]) {
                    codeDocument.deleteAnnotation(annotations.get(last[1] - 1).getPageHandler(), last[1] - 1);
                }
                return false;
            });
        }
        return true;
    }

    /** The page shown, two probes 100 px apart giving the scale. */
    @Override public List<PagePoint> toPageUnits(List<PointF> drawPoints) {
        if (codeDocument == null || drawPoints.isEmpty()) {
            return null;
        }
        final int page = currentPage;
        final CodecPageInfo info = codeDocument.getPageInfo(page);
        final List<PointF> withProbe = new ArrayList<>(drawPoints);
        final PointF first = drawPoints.get(0);
        withProbe.add(new PointF(first.x + 100, first.y));
        final List<PointF> normalized = toPagePoints(withProbe);
        if (info == null || normalized == null || normalized.size() != withProbe.size()) {
            return null;
        }
        final float unitsPerPx = Math.abs(normalized.get(normalized.size() - 1).x - normalized.get(0).x) * info.width / 100;
        final List<PagePoint> out = new ArrayList<>(drawPoints.size());
        for (int i = 0; i < drawPoints.size(); i++) {
            final PointF n = normalized.get(i);
            final boolean onPage = n.x >= 0 && n.x <= 1 && n.y >= 0 && n.y <= 1;
            out.add(onPage ? new PagePoint(page, new PointF(n.x * info.width, n.y * info.height), unitsPerPx) : null);
        }
        return out;
    }

    @Override public void addInk(int page, List<PointF[]> arcs, final int color, final float width) {
        if (codeDocument == null || arcs.isEmpty()) {
            return;
        }
        final PointF[][] array = arcs.toArray(new PointF[0][]);
        final float alpha = AppState.get().editAlphaColor;
        changePage(page, codecPage -> {
            codecPage.addAnnotation(Colors.toMupdfColor(color), array, width, alpha);
            return true;
        });
    }

    @Override public void underlineText(int color, float width, final AnnotationType type) {
        final int page = currentPage;
        final List<TextWord> selected = PageImageState.get().getSelectedWords(page);
        if (codeDocument == null || selected == null || selected.isEmpty()) {
            return;
        }
        com.foobnix.stats.ReadingTracker.count(com.foobnix.stats.StatsEvent.HIGHLIGHT);
        // one quad per line, in page units (TextWord.getOriginal), as VerticalModeController.underlineText
        final List<PointF> quads = new ArrayList<>();
        RectF line = null;
        final List<RectF> lines = new ArrayList<>();
        for (TextWord word : selected) {
            final RectF rect = word.getOriginal();
            if (rect == null) {
                continue;
            }
            if (line != null && line.top == rect.top && line.bottom == rect.bottom) {
                line.union(rect);
                continue;
            }
            line = new RectF(rect);
            lines.add(line);
        }
        for (RectF r : lines) {
            quads.add(new PointF(r.left, r.bottom));
            quads.add(new PointF(r.right, r.bottom));
            quads.add(new PointF(r.right, r.top));
            quads.add(new PointF(r.left, r.top));
        }
        if (quads.isEmpty()) {
            return;
        }
        final PointF[] array = quads.toArray(new PointF[0]);
        final float[] mupdfColor = Colors.toMupdfColor(color);
        changePage(page, codecPage -> {
            codecPage.addMarkupAnnotation(array, type, mupdfColor);
            return true;
        });
    }

    @Override public void addHighlightLines(final int page, List<RectF> lines, int color) {
        if (codeDocument == null || lines == null || lines.isEmpty()) {
            return;
        }
        final PointF[] quads = toQuadPoints(lines);
        final float[] mupdfColor = Colors.toMupdfColor(color);
        changePage(page, codecPage -> {
            codecPage.addMarkupAnnotation(quads, AnnotationType.HIGHLIGHT, mupdfColor);
            return true;
        });
    }

    /**
     * Before the book closes: saves the annotation changes, or asks first unless saving is
     * automatic (as VerticalModeController does).
     *
     * @return true when {@code then} is run later, after the save or the answer
     */
    public boolean saveAnnotationsBeforeClose(final Runnable then) {
        if (annotationsDecided || codeDocument == null || codeDocument.isRecycled() || !codeDocument.hasChanges()) {
            return false;
        }
        annotationsDecided = true;
        if (AppState.get().isSaveAnnotatationsAutomatically) {
            saveAnnotationsThen(then);
            return true;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.save_changes)
                .setMessage(bookPath)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialog, which) -> saveAnnotationsThen(then))
                .setNegativeButton(R.string.no, (dialog, which) -> then.run())
                .show();
        return true;
    }

    private void saveAnnotationsThen(final Runnable then) {
        final MyProgressDialog progress = MyProgressDialog.show(activity, activity.getString(R.string.saving_));
        progress.setCancelable(false);
        progress.show();
        annotationExecutor.execute(() -> {
            try {
                codeDocument.saveAnnotations(bookPath);
            } catch (Exception e) {
                LOG.e(e);
            }
            activity.runOnUiThread(() -> {
                progress.dismiss();
                then.run();
            });
        });
    }

    @Override
    public void getOutline(final com.foobnix.android.utils.ResultResponse<List<OutlineLinkWrapper>> outlineResonse,
                           boolean forse) {

        if (Apps.isDestroyedActivity(activity)) {
            return;
        }

        if (codeDocument == null) {
            outlineResonse.onResultRecive(Collections.emptyList());
            return;
        }

        if (outline == null) {
            outline = (ArrayList<OutlineLinkWrapper>) CacheZipUtils.loadJavaCache(getCurrentBook());
            if (outline != null) {
                outlineResonse.onResultRecive(outline);
                return;
            }

            outline = new ArrayList<>();
            AppsConfig.executorServiceSingle.execute(() -> {

                try {
                    for (OutlineLink ol : codeDocument.getOutline()) {
                        if (TempHolder.get().loadingCancelled.get()) {
                            return;
                        }

                        if (Apps.isDestroyedActivity(activity)) {
                            return;
                        }

                        if (codeDocument.isRecycled()) {
                            return;
                        }
                        if (TxtUtils.isNotEmpty(ol.getTitle())) {
                            if (ol.getLink() != null && ol.getLink()
                                                          .startsWith("#") && !ol.getLink()
                                                                                 .startsWith("#0")) {
                                outline.add(
                                        new OutlineLinkWrapper(ol.getTitle(), ol.getLink(), ol.getLevel(), ol.linkUri));
                            } else {
                                int page = MuPdfLinks.getLinkPageWrapper(ol.docHandle, ol.linkUri) + 1;
                                outline.add(
                                        new OutlineLinkWrapper(ol.getTitle(), "#" + page, ol.getLevel(), ol.linkUri));
                            }
                        }
                    }

                    CacheZipUtils.savaJavaCache(outline, getCurrentBook());

                    // setOutline(outline);
                    if (outlineResonse != null) {
                        getActivity().runOnUiThread(new Runnable() {

                            @Override public void run() {
                                outlineResonse.onResultRecive(outline);
                            }
                        });

                    }
                } catch (Exception e) {
                    LOG.e(e);
                }

            });

        } else {
            outlineResonse.onResultRecive(outline);
        }

    }

    @Override public void recyclePage(int number) {
        if (codeDocument == null) {
            return;
        }
        try {
            CodecPage page = codeDocument.getPage(number);
            page.recycle();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    @Override public void doSearch(final String text, final com.foobnix.android.utils.ResultResponse<Integer> result,
                                   int firstPage, int lastPage) {
        if (searchTask != null && searchTask.getStatus() != CopyAsyncTask.Status.FINISHED) {
            return;
        }

        searchTask = new CopyAsyncTask() {

            @Override protected Object doInBackground(Object... params) {
                try {
                    PageImageState.get()
                                  .cleanSelectedWords();
                    String textLowCase = text.toLowerCase(Locale.US);
                    String bookPath = getBookPath();
                    int prev = -1;

                    boolean nextWorld = false;
                    String firstPart = "";
                    TextWord firstWord = null;
                    int firstWordIndex = 0;

                    PageSearcher pageSearcher = new PageSearcher();
                    pageSearcher.setTextForSearch(text);
                    pageSearcher.setListener(new PageSearcher.OnWordSearched() {
                        @Override public void onSearch(TextWord word, Object data) {
                            if (!(data instanceof Integer)) return;
                            Integer pageNumber = (Integer) data;
                            LOG.d("Find on page_", pageNumber, text, word);
                            List<TextWord> selectedWords = PageImageState.get()
                                                                         .getSelectedWords(pageNumber);
                            if (selectedWords == null || selectedWords.size() <= 0) {
                                result.onResultRecive(pageNumber);
                                LOG.d("Find on page", pageNumber, text);
                            }
                            if (selectedWords == null || !selectedWords.contains(word)) {
                                PageImageState.get()
                                              .addWord(pageNumber, word);
                            }
                        }
                    });

                    for (int i = firstPage; i < lastPage; i++) {
                        if (!TempHolder.isSeaching) {
                            result.onResultRecive(Integer.MAX_VALUE);
                            return null;
                        }

                        if (isClosed) {
                            TempHolder.isSeaching = false;
                            return null;
                        }
                        if (i > 1) {
                            result.onResultRecive(i * -1);
                        }

                        TextWord[][] pageText = getPageText(i);
                        recyclePage(i);
                        if (pageText == null) {
                            continue;
                        }
                        int index = 0;
                        List<TextWord> find = new ArrayList<TextWord>();
                        for (TextWord[] line : pageText) {
                            find.clear();
                            index = 0;
                            for (TextWord word : line) {
                                if (AppState.get().selectingByLetters) {
                                    String it = String.valueOf(textLowCase.charAt(index));
                                    if (word.w.toLowerCase(Locale.US)
                                              .equals(it)) {
                                        index++;
                                        find.add(word);
                                    } else {
                                        index = 0;
                                        find.clear();
                                    }

                                    if (index == text.length()) {
                                        index = 0;
                                        if (prev != i) {
                                            result.onResultRecive(i);
                                            prev = i;
                                        }
                                        for (TextWord t : find) {
                                            PageImageState.get()
                                                          .addWord(i, t);
                                        }
                                    }

                                } else if (word.w.toLowerCase(Locale.US)
                                                 .contains(textLowCase)) {
                                    LOG.d("Contains 1", word.w);
                                    if (prev != i) {
                                        result.onResultRecive(i);
                                        prev = i;
                                    }
                                    PageImageState.get()
                                                  .addWord(i, word);
                                } else if (word.w.length() >= 3 && word.w.endsWith("-")) {
                                    nextWorld = true;
                                    firstWord = word;
                                    firstWordIndex = i;
                                    firstPart = word.w.replace("-", "");
                                } else if (nextWorld && (firstPart + word.w.toLowerCase(Locale.US)).contains(text)) {
                                    LOG.d("Contains 2", firstPart, word.w, text);
                                    PageImageState.get()
                                                  .addWord(firstWordIndex, firstWord);
                                    PageImageState.get()
                                                  .addWord(i, word);
                                    nextWorld = false;
                                    firstWord = null;
                                    firstPart = "";
                                    if (prev != firstWordIndex) {
                                        result.onResultRecive(firstWordIndex);
                                        prev = firstWordIndex;
                                    }
                                    if (prev != i) {
                                        result.onResultRecive(i);
                                        prev = i;
                                    }

                                } else if (nextWorld && TxtUtils.isNotEmpty(word.w)) {
                                    nextWorld = false;
                                    firstWord = null;
                                }
                                pageSearcher.addWord(new PageSearcher.WordData(word, i));
                            }
                        }

                    }
                    result.onResultRecive(-1);
                } catch (Exception e) {
                    result.onResultRecive(-1);
                }
                TempHolder.isSeaching = false;
                return null;
            }

            @Override protected void onPostExecute(Object result) {
                EventBus.getDefault()
                        .post(new InvalidateMessage());
            }

            ;

        }.execute();

    }

    public String getBookPath() {
        return bookPath;
    }

    @Override public File getCurrentBook() {
        return new File(getBookPath());
    }

    @Override public String getTitle() {
        return getTitle(getBookPath());
    }

    @Override public void alignDocument() {
        PageImageState.get().isAutoFit = true;
        EventBus.getDefault()
                .post(new MessageAutoFit(getCurentPage()));
    }

    @Override public void centerHorizontal() {
        PageImageState.get().isAutoFit = true;
        EventBus.getDefault()
                .post(new MessageCenterHorizontally(getCurentPage()));
    }

}