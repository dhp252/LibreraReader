package com.foobnix.webdav;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.ui2.fragment.WebDavFragment2;

/** WebDAV settings: the section in the preferences and the dialog behind it. */
public class WebDavDialog {

    /** Wires the WebDAV section of fragment_preferences.xml. */
    public static void bindPreferences(final Activity a, final View root) {
        final View header = root.findViewById(R.id.webdavHeader);
        if (header == null) {
            return;
        }
        TintUtil.setSectionFillColor(header, TintUtil.color);
        root.findViewById(R.id.webdavSettings).setOnClickListener(v -> show(a, () -> updatePreferences(a, root)));
        root.findViewById(R.id.webdavSyncNow).setOnClickListener(v -> WebDav.runSyncService(a, true));
        root.findViewById(R.id.webdavBooks).setOnClickListener(v -> WebDavFragment2.open(a));
        updatePreferences(a, root);
    }

    /** Server and last run, as shown under the section header. */
    public static void updatePreferences(Context c, View root) {
        final TextView status = root == null ? null : root.findViewById(R.id.webdavStatus);
        if (status == null || c == null) {
            return;
        }
        final String url = WebDav.getUrl(c);
        final boolean enabled = WebDav.isEnabled(c);
        if (TxtUtils.isEmpty(url)) {
            status.setText(R.string.webdav_not_set_up);
        } else if (!enabled) {
            status.setText(url + " - " + c.getString(R.string.webdav_off));
        } else {
            final String last = WebDav.getLastSyncInfo(c);
            status.setText(last.isEmpty() ? url : url + "\n" + last);
        }
        root.findViewById(R.id.webdavSyncNow).setVisibility(enabled ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.webdavBooks).setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    /** Server, user, password and the on/off switch, with a connection test. */
    public static void show(final Activity a, final Runnable onSaved) {
        final View view = LayoutInflater.from(a).inflate(R.layout.dialog_webdav, null, false);
        final EditText url = view.findViewById(R.id.webdavUrl);
        final EditText user = view.findViewById(R.id.webdavUser);
        final EditText password = view.findViewById(R.id.webdavPassword);
        final CheckBox enabled = view.findViewById(R.id.webdavEnabled);
        final TextView result = view.findViewById(R.id.webdavTestResult);

        url.setText(WebDav.getUrl(a));
        user.setText(WebDav.getUser(a));
        password.setText(WebDav.getPassword(a));
        // a first set-up switches it on: that is what filling in a server is for
        enabled.setChecked(WebDav.isEnabled(a) || TxtUtils.isEmpty(WebDav.getUrl(a)));

        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(R.string.webdav_sync)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.webdav_test, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    test(a, url.getText().toString(), user.getText().toString(), password.getText().toString(), result));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final boolean wasEnabled = WebDav.isEnabled(a);
                WebDav.save(a, url.getText().toString(), user.getText().toString(), password.getText().toString(),
                        enabled.isChecked());
                dialog.dismiss();
                if (onSaved != null) {
                    onSaved.run();
                }
                WebDav.runSyncService(a, true);
                if (wasEnabled != WebDav.isEnabled(a)) {
                    // the WebDAV tab comes and goes with the sync
                    WebDavFragment2.onEnabledChanged(a);
                }
            });
        });
        dialog.show();
    }

    private static void test(final Activity a, final String url, final String user, final String password, final TextView result) {
        result.setVisibility(View.VISIBLE);
        result.setText(R.string.please_wait);
        new Thread(() -> {
            final String answer = WebDav.testConnection(url, user, password);
            a.runOnUiThread(() -> result.setText(answer.startsWith("!")
                    ? a.getString(R.string.fail) + ": " + answer.substring(1)
                    : a.getString(R.string.webdav_test_ok, answer)));
        }, "@T WebDAV test").start();
    }
}
