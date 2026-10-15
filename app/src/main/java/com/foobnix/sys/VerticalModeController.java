package com.foobnix.sys;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.Toast;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.ResultResponse;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.model.AppBook;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.PageUrl;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.model.AnnotationType;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.pdf.info.model.OutlineLinkWrapper;
import com.foobnix.pdf.info.view.MyProgressDialog;
import com.foobnix.pdf.info.widget.PrefDialogs;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.pdf.search.activity.msg.MessagePageXY;
import com.foobnix.tts.TTSEngine;
import com.foobnix.tts.TTSHighlight;
import com.foobnix.tts.TTSService;

import org.ebookdroid.common.settings.CoreSettings;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.core.Page;
import org.ebookdroid.core.PageText;
import org.ebookdroid.core.codec.Annotation;
import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.CodecPage;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.droids.mupdf.codec.MuPdfLinks;
import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.ebookdroid.ui.viewer.ViewerActivityController;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VerticalModeController extends DocumentController {

    private static final double ZOOM_VALUE = 0.05;
    Handler handler;
    Thread t = new Thread("@T AutoScroll");

    long begin = 0;
    float currentZoom, currentX, currentY, pageN;
    boolean isLocked;
    List<OutlineLinkWrapper> outline;
    private ViewerActivityController ctr;

    public VerticalModeController(final Activity activity, final ViewerActivityController ctr) {
        super(activity);
        this.ctr = ctr;
        CoreSettings.getInstance().fullScreen = AppState.get().fullScreenMode == AppState.FULL_SCREEN_FULLSCREEN;
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void recyclePage(int pageNumber) {
        if (ctr == null) {
            return;
        }
        try {
            CodecPage page = ctr.getDecodeService().getCodecDocument().getPage(pageNumber);
            page.recycle();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    @Override
    public CodecDocument getCodecDocument() {
        if (ctr == null || ctr.getDecodeService() == null) {
            return null;
        }
        return ctr.getDecodeService().getCodecDocument();
    }

    @Override
    public int getBookWidth() {
        return ctr.getView().getWidth();
    }

    public float getPercentage1() {
        float p = (float) ctr.getView().getScrollY() / ctr.getDocumentController().getBottomScrollLimit();


        LOG.d("getPercentage-", ctr.getView().getScrollY(), ctr.getDocumentController().getBottomScrollLimit());
        LOG.d("getPercentage-", p);
        return p;
        // return super.getPercentage();
    }

    @Override
    public void onScrollYPercent(float value) {
        if (true) {
            int page = (int) value * getPageCount();
            ctr.getDocumentController().goToPage(page);
            return;
        }

        int res = Math.round(ctr.getDocumentController().getBottomScrollLimit() * value);
        ctr.getView().scrollTo(ctr.getView().getScrollX(), res);
        LOG.d("getPercentage-Scroll to", value, ctr.getDocumentController().getBottomScrollLimit(), res);
    }

    @Override
    public int getBookHeight() {
        return ctr.getView().getHeight();
    }

    @Override
    public void cleanImageMatrix() {
    }

    @Override
    public void onGoToPage(int page) {
        if (checkReadingTimer()) {
            return;
        }
        ctr.getDocumentController().goToPage(page - 1);
    }

    @Override
    public int getCurentPageFirst1() {
        return getCurentPage();
    }

    @Override
    public void onGoToPage(final int page, final float offsetX, final float offsetY) {
    }

    @Override
    public float getOffsetX() {
        return ctr.getView().getScrollX();
    }

    @Override
    public PageUrl getPageUrl(int page) {
        return PageUrl.build(getCurrentBook().getPath(), page, Dips.screenWidth(), Dips.screenHeight());
    }

    @Override
    public float getOffsetY() {
        return ctr.getView().getScrollY();
    }

    @Override
    public void onCrop() {
        try {
            // AppSP.get().isCrop = !AppSP.get().isCrop;
            ctr.toggleCrop(AppSP.get().isCrop);
        } catch (final Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean hasPDFAnnotations() {
        return ctr.getDecodeService().hasAnnotationChanges();
    }

    @Override
    public void clearSelectedText() {
        EventBus.getDefault().post(new MessagePageXY(MessagePageXY.TYPE_HIDE));
        ctr.getDocumentController().clearSelectedText();
    }

    @Override
    public void onSrollLeft() {
        // ctr.getDocumentController().getView().startPageScroll(5, 0);
        ctr.getDocumentController().getView().scrollBy(Dips.DP_3, 0);
    }

    @Override
    public void onSrollRight() {
        // ctr.getDocumentController().getView().startPageScroll(-5, 0);
        ctr.getDocumentController().getView().scrollBy(-1 * Dips.DP_3, 0);
    }

    @Override
    public void onScrollUp() {
        ctr.getDocumentController().getView().scrollBy(0, -1 * AppState.get().mouseWheelSpeed);

    }

    @Override
    public void onScrollDown() {
        ctr.getDocumentController().getView().scrollBy(0, AppState.get().mouseWheelSpeed);
    }

    @Override
    public void saveSettings() {
        ctr.onPause();
    }

    @Override
    public void onNextPage(final boolean animate) {
        int page = ctr.getDocumentModel().getCurrentDocPageIndex() + 1;

        // if (AppState.get().isLoopAutoplay) {
        LOG.d("onNextPage", page, getPageCount());
        if (AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN && page == getPageCount()) {
            page = 0;
        }
        //}
        ctr.getDocumentController().goToPage(page, animate);


    }

    @Override
    public void onPrevPage(final boolean animate) {
        int page = ctr.getDocumentModel().getCurrentDocPageIndex() - 1;

        // if (AppState.get().isLoopAutoplay) {
        LOG.d("onPrevPage", page, getPageCount());
        if (AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN && page == -1) {
            page = getPageCount() - 1;
        }
        //}

        ctr.getDocumentController().goToPage(page, animate);
    }

    @Override
    public void onClickTop() {
        final int page = ctr.getDocumentModel().getCurrentDocPageIndex();
        ctr.getDocumentController().goToPage(page, (float) 0.25, 0);
    }

    @Override
    public void onNextScreen(boolean animate) {
        int nextScreenScrollBy = AppState.get().nextScreenScrollBy;
        LOG.d("nextScreenScrollBy", nextScreenScrollBy, "animate", animate);


        int before = ctr.getDocumentController().getView().getScrollY();
        if (animate) {
            ctr.getDocumentController().getView().startPageScroll(0, 1 * nextScreenScrollBy * getScrollValue() / 100);
        } else {
            ctr.getDocumentController().getView().scrollBy(0, 1 * nextScreenScrollBy * getScrollValue() / 100);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int after = ctr.getDocumentController().getView().getScrollY();
                if (AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN && before == after) {
                    ctr.getDocumentController().getView().stopScroller();
                    ctr.getDocumentController().goToPage(0);
                }
            }
        }, 100);


    }

    @Override
    public void onPrevScreen(boolean animate) {
        int nextScreenScrollBy = AppState.get().nextScreenScrollBy;
        int before = ctr.getDocumentController().getView().getScrollY();

        if (animate) {
            ctr.getDocumentController().getView().startPageScroll(0, -1 * nextScreenScrollBy * getScrollValue() / 100);
        } else {
            ctr.getDocumentController().getView().scrollBy(0, -1 * nextScreenScrollBy * getScrollValue() / 100);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int after = ctr.getDocumentController().getView().getScrollY();
                LOG.d(" before == after", before, after);
                if (AppSP.get().readingMode == AppState.READING_MODE_MUSICIAN && before == after) {
                    ctr.getDocumentController().getView().stopScroller();
                    ctr.getDocumentController().goToPage(getPageCount() - 1);
                }
            }
        }, 100);
    }

    @Override
    public void deleteAnnotation(long pageHanderl, final int page, final int index) {
        ctr.getDecodeService().deleteAnnotation(pageHanderl, page - 1, index, new ResultResponse<List<Annotation>>() {

            @Override
            public boolean onResultRecive(List<Annotation> arg0) {
                ctr.getDocumentModel().getPageObject(page - 1).annotations = arg0;
                ctr.getDocumentController().toggleRenderingEffects();
                return false;
            }
        });

    }

    @Override
    public boolean undoLastAnnotation() {
        final List<int[]> group = popCreatedGroup();
        if (group.isEmpty() || ctr == null || ctr.getDocumentModel() == null) {
            return false;
        }
        // the deletions land later: count those already asked for on each page
        final Map<Integer, Integer> asked = new HashMap<>();
        boolean any = false;
        for (int[] last : group) {
            final Page page = ctr.getDocumentModel().getPageByDocIndex(last[0]);
            final int before = asked.containsKey(last[0]) ? asked.get(last[0]) : 0;
            // the newest annotation is only ours while the page still has the count it had after it
            if (page == null || page.annotations == null || page.annotations.size() - before != last[1]) {
                continue;
            }
            final Annotation newest = page.annotations.get(last[1] - 1);
            deleteAnnotation(newest.getPageHandler(), newest.getPage(), newest.getIndex());
            asked.put(last[0], before + 1);
            any = true;
        }
        return any;
    }

    @Override
    public List<PagePoint> toPageUnits(List<PointF> drawPoints) {
        if (ctr == null || ctr.getDocumentModel() == null || ctr.getDocumentController() == null) {
            return null;
        }
        final float zoom = ctr.getZoomModel().getZoom();
        final int scrollX = ctr.getDocumentController().getView().getScrollX();
        final int scrollY = ctr.getDocumentController().getView().getScrollY();
        final int first = ctr.getDocumentController().getFirstVisiblePage();
        final int last = ctr.getDocumentController().getLastVisiblePage() + 1;
        final List<PagePoint> out = new ArrayList<>(drawPoints.size());
        for (PointF p : drawPoints) {
            PagePoint found = null;
            for (Page page : ctr.getDocumentModel().getPages(first, last)) {
                final RectF bounds = page.getBounds(zoom);
                final float x = p.x + scrollX;
                final float y = p.y + scrollY;
                if (!bounds.contains(x, y)) {
                    continue;
                }
                // as saveChanges: the box on screen, then the full page under a crop
                float normX = (x - bounds.left) / bounds.width();
                float normY = (y - bounds.top) / bounds.height();
                float shownWidth = 1;
                final RectF crop = page.getCroppedBounds();
                if (crop != null) {
                    normX = crop.left + normX * crop.width();
                    normY = crop.top + normY * crop.height();
                    shownWidth = crop.width();
                }
                found = new PagePoint(page.index.docIndex, new PointF(normX * page.cpi.width, normY * page.cpi.height),
                        page.cpi.width * shownWidth / bounds.width());
                break;
            }
            out.add(found);
        }
        return out;
    }

    @Override
    public void addInk(final int page, List<PointF[]> arcs, int color, float width) {
        if (ctr == null || ctr.getDecodeService() == null || arcs.isEmpty()) {
            return;
        }
        final int group = annotationGroup();
        ctr.getDecodeService().addInk(page, arcs.toArray(new PointF[0][]), color, width, AppState.get().editAlphaColor,
                new ResultResponse<Pair<Integer, List<Annotation>>>() {
                    @Override
                    public boolean onResultRecive(Pair<Integer, List<Annotation>> p) {
                        ctr.getDocumentModel().getPageObject(p.first).annotations = p.second;
                        ctr.getDocumentController().toggleRenderingEffects();
                        recordCreatedAnnotation(p.first, p.second == null ? 0 : p.second.size(), group);
                        return false;
                    }
                });
    }

    @Override
    public com.foobnix.ai.AiCategory aiCategoryAt(float x, float y) {
        final com.foobnix.ai.AiHighlights highlights = com.foobnix.ai.AiHighlights.get();
        if (!highlights.isVisible() || highlights.count() == 0) {
            return null;
        }
        final List<PagePoint> at = toPageUnits(java.util.Collections.singletonList(new PointF(x, y)));
        if (at == null || at.isEmpty() || at.get(0) == null) {
            return null;
        }
        final PagePoint p = at.get(0);
        final Page page = ctr.getDocumentModel().getPageByDocIndex(p.page);
        if (page == null || page.cpi == null || page.cpi.width <= 0 || page.cpi.height <= 0) {
            return null;
        }
        // bands are in page-relative coordinates, as the page's words
        return highlights.categoryAt(p.page, p.point.x / page.cpi.width, p.point.y / page.cpi.height);
    }

    /** The scroll view takes the first point as a selection anchor's corner (AdvGuestureDetector). */
    @Override
    public boolean selectTextBetween(float x1, float y1, float x2, float y2) {
        return super.selectTextBetween(x1 + Dips.DP_36, y1 + Dips.DP_36 / 2f, x2, y2);
    }

    @Override
    public boolean isCropCurrentBook() {
        AppBook bookSettings = SettingsManager.getBookSettings();
        if (bookSettings == null) {
            return false;
        }
        return bookSettings.cp;
    }

    @Override
    public void saveChanges(final List<PointF> points, final int color) {
        final float zoom = ctr.getZoomModel().getZoom();
        int scrollX = ctr.getDocumentController().getView().getScrollX();
        int scrollY = ctr.getDocumentController().getView().getScrollY();

        final Map<Integer, List<PointF>> result = new HashMap<Integer, List<PointF>>();

        final int first = ctr.getDocumentController().getFirstVisiblePage();
        final int last = ctr.getDocumentController().getLastVisiblePage() + 1;
        LOG.d("first", first, "last", last);

        for (PointF p : points) {
            RectF tapRect = new RectF(p.x, p.y, p.x, p.y);
            tapRect.offset(scrollX, scrollY);

            Iterable<Page> pages = ctr.getDocumentModel().getPages(first, last);
            for (Page page : pages) {
                RectF pbounds = page.getBounds(zoom);

                if (RectF.intersects(pbounds, tapRect)) {
                    int pNumber = page.index.docIndex;
                    List<PointF> list = result.get(pNumber);
                    if (list == null) {
                        list = new ArrayList<PointF>();
                    }

                    // Position of the touch within the on-screen page box (0..1).
                    float normX = (p.x + scrollX - pbounds.left) / pbounds.width();
                    float normY = (p.y + scrollY - pbounds.top) / pbounds.height();

                    // When "Crop White Space" is enabled the on-screen page shows
                    // only the cropped sub-region of the full page, so remap the
                    // normalized point back into the full page before converting
                    // to PDF coordinates. Annotations are always stored in full
                    // page coordinates so they render correctly cropped or not.
                    RectF crop = page.getCroppedBounds();
                    if (crop != null) {
                        normX = crop.left + normX * crop.width();
                        normY = crop.top + normY * crop.height();
                    }

                    p.x = normX * page.cpi.width;
                    p.y = normY * page.cpi.height;

                    list.add(p);
                    result.put(pNumber, list);
                }
            }

        }

        float width = AppState.get().editLineWidth * 3;
        float alpha = AppState.get().editAlphaColor;

        ctr.getDecodeService().addAnnotation(result, color, width, alpha, new ResultResponse<Pair<Integer, List<Annotation>>>() {

            @Override
            public boolean onResultRecive(Pair<Integer, List<Annotation>> p) {
                ctr.getDocumentModel().getPageObject(p.first).annotations = p.second;
                ctr.getDocumentController().toggleRenderingEffects();
                recordCreatedAnnotation(p.first, p.second == null ? 0 : p.second.size());
                return false;
            }

        });

    }

    @Override
    public synchronized String getTextForPage(int page) {
        String pageHTML = ctr.getDecodeService().getPageHTML(page);

        pageHTML = TxtUtils.replaceHTMLforTTS(pageHTML);

        pageHTML = pageHTML.replace(TxtUtils.TTS_PAUSE, " ");
        pageHTML = pageHTML.replace(TxtUtils.NON_BREAKE_SPACE, " ");

        return pageHTML;
    }

    @Override
    public synchronized String getPageHtml() {
        String pageHTML = ctr.getDecodeService().getPageHTML(getCurentPageFirst1() - 1);
        pageHTML = TxtUtils.replaceHTMLforTTS(pageHTML);
        pageHTML = pageHTML.replace(TxtUtils.TTS_PAUSE, TxtUtils.TTS_PAUSE_VIEW);
        return pageHTML;
    }

    @Override
    public List<PageLink> getLinksForPage(int page) {
        return ctr.getDecodeService().getLinksForPage(page);
    }

    @Override
    public void underlineText(int color, float width, AnnotationType type) {
        if (ctr == null || ctr.getDocumentController() == null || ctr.getDocumentModel() == null) {
            LOG.d("Can't underlineText");
            return;
        }
        final int first = ctr.getDocumentController().getFirstVisiblePage();
        final int last = ctr.getDocumentController().getLastVisiblePage();
        boolean counted = false;
        for (int i = first; i < last + 1; i++) {
            final Page page = ctr.getDocumentModel().getPageByDocIndex(i);
            // an empty selection would still create an (empty, invisible) markup annotation
            if (page == null || page.selectedText == null || page.selectedText.isEmpty()) {
                continue;
            }
            if (!counted) {
                // one selection over two pages is one highlight
                com.foobnix.stats.ReadingTracker.count(com.foobnix.stats.StatsEvent.HIGHLIGHT);
                counted = true;
            }
            List<TextWord> texts = page.selectedText;

            final ArrayList<PointF> quadPoints = new ArrayList<PointF>();


            ArrayList<RectF> rects = new ArrayList<RectF>();
            RectF prevRect = null;
            for (TextWord text : texts) {
                RectF rect = text.getOriginal();
                if (prevRect != null && prevRect.top == rect.top && prevRect.bottom == rect.bottom) {
                    prevRect.union(rect);
                    continue;
                }
                prevRect = new RectF(rect);
                rects.add(prevRect);
            }

            for (RectF rect : rects) {
                quadPoints.add(new PointF(rect.left, rect.bottom));
                quadPoints.add(new PointF(rect.right, rect.bottom));
                quadPoints.add(new PointF(rect.right, rect.top));
                quadPoints.add(new PointF(rect.left, rect.top));

            }

            PointF[] array = quadPoints.toArray(new PointF[quadPoints.size()]);
            final int docIndex = i;
            ctr.getDecodeService().underlineText(i, array, color, type, new ResultResponse<List<Annotation>>() {

                @Override
                public boolean onResultRecive(List<Annotation> arg0) {
                    page.annotations = arg0;
                    page.selectedText = new ArrayList<TextWord>();
                    ctr.getDocumentController().toggleRenderingEffects();
                    recordCreatedAnnotation(docIndex, arg0 == null ? 0 : arg0.size());
                    return false;
                }
            });

        }
    }

    @Override
    public void addHighlightLines(final int page, List<RectF> lines, int color) {
        if (ctr == null || ctr.getDocumentModel() == null || ctr.getDecodeService() == null
                || lines == null || lines.isEmpty()) {
            return;
        }
        final Page target = ctr.getDocumentModel().getPageByDocIndex(page);
        ctr.getDecodeService().underlineText(page, toQuadPoints(lines), color, AnnotationType.HIGHLIGHT,
                new ResultResponse<List<Annotation>>() {

                    @Override
                    public boolean onResultRecive(List<Annotation> annotations) {
                        if (target != null) {
                            target.annotations = annotations;
                        }
                        if (ctr.getDocumentController() != null) {
                            ctr.getDocumentController().toggleRenderingEffects();
                        }
                        recordCreatedAnnotation(page, annotations == null ? 0 : annotations.size());
                        return false;
                    }
                });
    }

    @Override
    public boolean readAloudFromTap(int x, int y) {
        if (!AppState.get().isTTSDoubleTapRead || !TTSEngine.get().isPlaying()) {
            return false;
        }
        if (ctr == null || ctr.getDocumentController() == null || ctr.getDocumentModel() == null || getCurrentBook() == null) {
            return false;
        }
        final float zoom = ctr.getZoomModel().getZoom();
        final float slop = Dips.dpToPx(6);
        final RectF tap = new RectF(x - slop, y - slop, x + slop, y + slop);
        tap.offset(ctr.getDocumentController().getView().getScrollX(), ctr.getDocumentController().getView().getScrollY());

        final int first = ctr.getDocumentController().getFirstVisiblePage();
        final int last = ctr.getDocumentController().getLastVisiblePage() + 1;
        for (Page page : ctr.getDocumentModel().getPages(first, last)) {
            final RectF bounds = page.getBounds(zoom);
            final TextWord[][] texts = PageText.of(page);
            if (texts == null || !RectF.intersects(bounds, tap)) {
                continue;
            }
            final List<TextWord> words = TTSHighlight.words(texts);
            for (int i = 0; i < words.size(); i++) {
                final RectF wordRect = page.getPageRegion(bounds, words.get(i));
                if (wordRect != null && RectF.intersects(wordRect, tap)) {
                    TTSService.playBookPage(page.index.docIndex, getCurrentBook().getPath(), TTSHighlight.anchorFrom(words, i),
                            (float) i / words.size(), getBookWidth(), getBookHeight(), BookCSS.get().fontSizeSp, getTitle());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void redrawReadAloud() {
        if (ctr != null && ctr.getDocumentController() != null) {
            ctr.getDocumentController().redrawView();
        }
    }

    @Override
    public void onAutoScroll() {
        if (t.isAlive()) {
            return;
        }
        begin = System.currentTimeMillis();
        t = new Thread(new Runnable() {

            @Override
            public void run() {
                while (AppState.get().isAutoScroll) {
                    boolean repeat = false;

                    long l = System.currentTimeMillis() - begin;
                    if (!AppState.get().isLoopAutoplay && l > TimeUnit.HOURS.toMillis(1)) {
                        break;
                    }

                    if (AppState.get().isLoopAutoplay && ctr.getDocumentController().getScrollLimits().bottom == ctr.getDocumentController().getView().getScrollY()) {
                        repeat = true;
                        try {
                            Thread.sleep(3000);
                            LOG.d("Sleep 3000");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    auto();

                    if (repeat) {
                        try {
                            Thread.sleep(3000);
                            LOG.d("Sleep 3000");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        float x = AppState.get().autoScrollSpeed;

                        if (x <= 50) {
                            x = x * 2;
                        } else {
                            final int x1 = 51;
                            final int y1 = 101;

                            final int x2 = 149;
                            final int y2 = 149;
                            // x = (x - y1) * (x2 - x1) / (y2 - y1) + x1;
                            x = (x - x1) * (y2 - y1) / (x2 - x1) + y1;

                        }
                        LOG.d("TEST", "Speed after" + x);

                        Thread.sleep(Math.max(AppState.MAX_SPEED - (int) x + 5, 5));
                    } catch (final InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }, "@T AutoScroll 2");
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    public void auto() {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {

                if (AppState.get().isLoopAutoplay && ctr.getDocumentController().getScrollLimits().bottom == ctr.getDocumentController().getView().getScrollY()) {
                    LOG.d("onScrollY 01");
                    onScrollY(0);
                } else {
                    ctr.getDocumentController().getView().scrollBy(0, 1);
                }
            }
        });
    }

    @Override
    public void onScrollY(final int value) {
        final int x = ctr.getDocumentController().getView().getScrollX();
        ctr.getDocumentController().getView().scrollTo(x, value);
        LOG.d("onScrollY", value);
    }

    public int getScrollValue() {
        int value = ctr.getDocumentController().getView().getHeight() - Dips.dpToPx(4);

        View titleBar = activity.findViewById(R.id.titleBar);
        if (titleBar.getVisibility() == View.VISIBLE) {
            value = value - titleBar.getHeight();
        }
        View progress = activity.findViewById(R.id.progressDraw);
        if (progress.getVisibility() == View.VISIBLE) {
            value = value - progress.getHeight();
        }

        return value;
    }

    @Override
    public void onZoomInc() {
        final float zoom = ctr.getZoomModel().getZoom();
        ctr.getZoomModel().setZoom((float) (zoom + ZOOM_VALUE), false);
        commit();

    }

    @Override
    public void onZoomInOut(int x, int y) {
        if (currentZoom == 0) {

            final float zoom = ctr.getZoomModel().getZoom();
            currentZoom = zoom;
            currentX = ctr.getDocumentController().getView().getScrollX();
            currentY = ctr.getDocumentController().getView().getScrollY();
            pageN = ctr.getDocumentController().getFirstVisiblePage();
            int w = Dips.screenWidth() / 2;// center y
            int h = Dips.screenHeight() / 2;// center x
            ctr.getDocumentController().getView().scrollBy(x - w, y - h);
            ctr.getZoomModel().setZoom(zoom + 2f, false);
            commit();
            isLocked = AppSP.get().isLocked;
            AppSP.get().isLocked = false;
        } else {
            ctr.getZoomModel().setZoom(currentZoom, false);
            if (pageN == ctr.getDocumentController().getFirstVisiblePage()) {
                ctr.getDocumentController().getView().scrollTo((int) currentX, (int) currentY);
                commit();
            } else {
                alignDocument();
            }

            currentZoom = 0;
            AppSP.get().isLocked = isLocked;
        }
    }

    @Override
    public void alignDocument() {
        int curentPage = ctr.getDocumentModel().getCurrentDocPageIndex();

        ctr.getZoomModel().initZoom(1);
        ctr.getZoomModel().commit();

        //ctr.getDocumentController().goToPage(curentPage, 0, 0);
        ctr.getDocumentController().goToPageAndCenter(curentPage);
        //ctr.getDocumentController().goToPageAndCenter(curentPage);


        //ctr.getDocumentController().toggleRenderingEffects();
    }

    @Override
    public void centerHorizontal() {
        final float zoom = ctr.getZoomModel().getZoom();
        int viewWidth = (int) ctr.getDocumentModel().getCurrentPageObject().getBounds(zoom).width();
        int currentY = ctr.getDocumentController().getView().getScrollY();

        int dx = (viewWidth - Dips.screenWidth()) / 2;
        ctr.getDocumentController().getView().scrollTo(dx, currentY);
        LOG.d("viewWidth", viewWidth, dx);
        // commit();
    }

    @Override
    public void updateRendering() {

        ctr.getDocumentController().toggleRenderingEffects();
    }

    @Override
    public void onZoomDec() {
        final float zoom = ctr.getZoomModel().getZoom();
        ctr.getZoomModel().setZoom((float) (zoom - ZOOM_VALUE), false);
        commit();

    }

    public void commit() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {

            @Override
            public void run() {
                ctr.getZoomModel().commit();
            }
        }, 2000);
    }

    public void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    @Override
    public void saveAnnotationsToFile() {
//        if (AppState.get().isSaveAnnotatationsAutomatically) {
//
//            String path = getCurrentBook().getAbsolutePath();
//            ctr.getDecodeService().saveAnnotations(path, new Runnable() {
//
//                @Override
//                public void run() {
//                }
//            });
//        }
    }

    @Override
    public void onCloseActivityFinal(Runnable run) {
        stopTimer();
        ctr.closeActivityFinal(run);
    }

    @Override
    public synchronized void onCloseActivityAdnShowInterstial() {
        TempHolder.get().loadingCancelled.set(true);
        handler.removeCallbacksAndMessages(null);
        if (ctr == null || ctr.getDecodeService() == null) {
            return;
        }
        final StringBuilder path = new StringBuilder(getCurrentBook().getAbsolutePath());


        if (ctr.getDecodeService().hasAnnotationChanges()) {
            if (AppState.get().isSaveAnnotatationsAutomatically) {
                final MyProgressDialog progress = MyProgressDialog.show(getActivity(), getActivity().getString(R.string.saving_));
                progress.setCancelable(false);
                progress.show();
                ctr.getDecodeService().saveAnnotations(path.toString(), new Runnable() {

                    @Override
                    public void run() {
                        LOG.d("saveAnnotations return 1");
                        progress.dismiss();
                        LOG.d("saveAnnotations return 2");
                        ctr.closeActivity(null);
                    }
                });
            } else {

                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.save_changes);

                builder.setMessage(getCurrentBook().getAbsolutePath());
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (!path.toString().equals(getCurrentBook().getAbsolutePath())) {
                            LOG.d("Save TO new file", path.toString());
                            File newBook = new File(path.toString());
                            newBook.delete();
                            try {
                                copy(getCurrentBook(), newBook);

                            } catch (IOException e) {
                                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }

                        }
                        final MyProgressDialog progress = MyProgressDialog.show(getActivity(), getActivity().getString(R.string.saving_));
                        progress.setCancelable(false);
                        progress.show();
                        ctr.getDecodeService().saveAnnotations(path.toString(), new Runnable() {

                            @Override
                            public void run() {
                                LOG.d("saveAnnotations return 1");
                                progress.dismiss();
                                LOG.d("saveAnnotations return 2");
                                ctr.closeActivity(null);
                            }
                        });
                    }

                    ;
                });
                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        ctr.closeActivity(null);
                    }
                });
                builder.setNeutralButton(R.string.rename, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PrefDialogs.selectFileDialog(activity, Arrays.asList(".pdf"), getCurrentBook(), new ResultResponse<String>() {

                            @Override
                            public boolean onResultRecive(String result) {

                                path.setLength(0);
                                path.append(result);

                                builder.setMessage(result);
                                builder.show();
                                return false;
                            }
                        });

                    }
                });
                builder.show();
            }

        } else {
            ctr.closeActivity(null);
        }
    }

    @Override
    public void onNightMode() {
        if (getCurrentBook() == null) {
            return;
        }

        if (true) {
            AppState.get().isDayNotInvert = !AppState.get().isDayNotInvert;
            saveSettings();
            restartActivity();
            return;
        }
        if (ExtUtils.isTextFomat(getCurrentBook().getPath())) {
            saveSettings();
            restartActivity();
        } else {
            ctr.toggleNightMode();
        }

    }

    @Override
    public void restartActivity() {
        try {
            ctr.getDocumentModel().recyclePages();
        } catch (Exception e) {
            LOG.e(e);
        }
        super.restartActivity();
    }

    @Override
    public void onFullScreen() {
        CoreSettings.getInstance().fullScreen = !CoreSettings.getInstance().fullScreen;
        activity.finish();
        activity.startActivity(activity.getIntent());
    }

    @Override
    public int getCurentPage() {
        try {
            return ctr.getDocumentModel().getCurrentViewPageIndex() + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    @Override
    public int getPageCount() {
        try {
            return ctr.getDocumentModel().getPageCount();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void doSearch(String text, ResultResponse<Integer> result, int firstPage, int lastPage) {
        ctr.doSearch(text, result, firstPage, lastPage);
    }

    @Override
    public void toPageDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final EditText number = new EditText(getActivity());

        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        number.setText(String.valueOf(getCurentPage()));

        builder.setView(number);
        builder.setTitle(R.string.go_to_page_dialog);
        final DialogInterface.OnClickListener onOkListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                int page = 1;
                try {
                    page = Integer.valueOf(number.getText().toString());
                } catch (final NumberFormatException e) {
                    number.setText("1");
                }
                if (page >= 0 && page <= getPageCount()) {
                    onGoToPage(page);
                    dialog.dismiss();
                }
            }
        };
        builder.setPositiveButton(R.string.go, onOkListener);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                dialog.dismiss();
            }
        });
        final AlertDialog dialog = builder.show();

        number.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            onOkListener.onClick(dialog, 0);
                            return true;
                        default:
                            break;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public synchronized void getOutline(final ResultResponse<List<OutlineLinkWrapper>> resultWrapper, boolean forseRealod) {
        if (outline != null) {
            resultWrapper.onResultRecive(outline);
            return;
        }


        outline = (ArrayList<OutlineLinkWrapper>) CacheZipUtils.loadJavaCache(getCurrentBook());
        if (outline != null) {
            resultWrapper.onResultRecive(outline);
            return;
        }

        ctr.getDocumentModel().decodeService.getOutline(new ResultResponse<List<OutlineLink>>() {

            @Override
            public boolean onResultRecive(List<OutlineLink> outlineLinks) {
                outline = new ArrayList<OutlineLinkWrapper>();
                if (outlineLinks == null) {
                    return resultWrapper.onResultRecive(null);
                }

                for (OutlineLink ol : outlineLinks) {
                    if (TempHolder.get().loadingCancelled.get()) {
                        return false;
                    }


                    try {
                        if(ctr.getDocumentModel().decodeService.getCodecDocument().isRecycled()){
                            return false;
                        }
                        if (TxtUtils.isNotEmpty(ol.getTitle())) {
                            if (ol.getLink() != null && ol.getLink().startsWith("#") && !ol.getLink().startsWith("#0")) {
                                outline.add(new OutlineLinkWrapper(ol.getTitle(), ol.getLink(), ol.getLevel(), ol.linkUri));
                            } else {
                                int page = MuPdfLinks.getLinkPageWrapper(ol.docHandle, ol.linkUri) + 1;
                                outline.add(new OutlineLinkWrapper(ol.getTitle(), "#" + page, ol.getLevel(), ol.linkUri));
                            }

                        }
                    } catch (Exception e) {
                        LOG.e(e);
                    }
                }
                CacheZipUtils.savaJavaCache(outline, getCurrentBook());
                resultWrapper.onResultRecive(outline);
                return true;
            }
        });
    }

    @Override
    public String getFootNote(String text, String chapter) {
        return ctr.getDocumentModel().decodeService.getFooterNote(text, chapter);
    }

    @Override
    public List<String> getMediaAttachments() {
        try {
            return ctr.getDocumentModel().decodeService.getAttachemnts();
        } catch (Exception e) {
            return Collections.EMPTY_LIST;

        }
    }

    public void onDestroy() {
        AppState.get().isAutoScroll = false;
    }

}
