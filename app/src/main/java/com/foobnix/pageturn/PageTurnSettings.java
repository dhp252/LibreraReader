package com.foobnix.pageturn;

import android.view.View;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;
import com.foobnix.android.utils.TxtUtils;

/** Binds include_page_turn.xml: the page turn style and the haptic tick (book mode only). */
public final class PageTurnSettings {

    private static final int[] STYLES = {PageTurn.STYLE_SLIDE, PageTurn.STYLE_COVER, PageTurn.STYLE_CURL,
            PageTurn.STYLE_NONE};

    private PageTurnSettings() {
    }

    public static void bind(View root) {
        View block = root.findViewById(R.id.pageTurnSettings);
        if (block == null) {
            return;
        }
        block.setVisibility(AppSP.get().readingMode == AppState.READING_MODE_BOOK ? View.VISIBLE : View.GONE);

        final TextView style = root.findViewById(R.id.pageTurnStyle);
        show(style);
        style.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            for (final int s : STYLES) {
                menu.getMenu().add(name(s)).setOnMenuItemClickListener(item -> {
                    choose(v, s);
                    show(style);
                    return false;
                });
            }
            menu.show();
        });

        CheckBox haptic = root.findViewById(R.id.isPageTurnHaptic);
        haptic.setChecked(AppState.get().isPageTurnHaptic);
        haptic.setOnCheckedChangeListener((b, isChecked) -> AppState.get().isPageTurnHaptic = isChecked);
    }

    private static void choose(View anyView, int style) {
        AppState.get().pageTurnStyle = style;
        // older builds reading the synced state still see the matching on/off
        AppState.get().isScrollAnimation = PageTurn.isAnimated(style);
        // the curl view needs a frame on screen before its first curl: show it now, not at the
        // first turn (the reader's CurlTurn hides it again if GL is unavailable)
        View curl = anyView.getRootView().findViewById(R.id.pageCurl);
        if (curl != null) {
            curl.setVisibility(style == PageTurn.STYLE_CURL ? View.VISIBLE : View.GONE);
        }
    }

    private static void show(TextView view) {
        view.setText(name(PageTurn.style(AppState.get().pageTurnStyle, AppState.get().isScrollAnimation)));
        TxtUtils.underlineTextView(view);
    }

    private static int name(int style) {
        switch (style) {
            case PageTurn.STYLE_COVER:
                return R.string.page_turn_cover;
            case PageTurn.STYLE_CURL:
                return R.string.page_turn_curl;
            case PageTurn.STYLE_NONE:
                return R.string.page_turn_none;
            default:
                return R.string.page_turn_slide;
        }
    }
}
