package com.foobnix.annotation;

import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.R;

/** Binds include_stylus.xml: what a stylus draws with when no annotation tool is picked. */
public final class StylusSettings {

    private static final int[] CHOICES = {StylusRules.SETTING_PEN, StylusRules.SETTING_HIGHLIGHTER, StylusRules.SETTING_OFF};

    private StylusSettings() {
    }

    public static void bind(View root) {
        final TextView tool = root.findViewById(R.id.stylusTool);
        if (tool == null) {
            return;
        }
        show(tool);
        tool.setOnClickListener(v -> {
            final PopupMenu menu = new PopupMenu(v.getContext(), v);
            for (final int choice : CHOICES) {
                menu.getMenu().add(name(choice)).setOnMenuItemClickListener(item -> {
                    AppState.get().stylusTool = choice;
                    show(tool);
                    return false;
                });
            }
            menu.show();
        });
    }

    private static void show(TextView view) {
        view.setText(name(AppState.get().stylusTool));
        TxtUtils.underlineTextView(view);
    }

    private static int name(int setting) {
        switch (setting) {
            case StylusRules.SETTING_OFF:
                return R.string.stylus_off;
            case StylusRules.SETTING_HIGHLIGHTER:
                return R.string.stylus_highlighter;
            default:
                return R.string.stylus_pen;
        }
    }
}
