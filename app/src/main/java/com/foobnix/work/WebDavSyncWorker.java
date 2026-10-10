package com.foobnix.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.foobnix.android.utils.LOG;
import com.foobnix.drive.GFile;
import com.foobnix.model.AppSP;
import com.foobnix.model.TagData;
import com.foobnix.pdf.search.activity.msg.MessageSync;
import com.foobnix.pdf.search.activity.msg.UpdateAllFragments;
import com.foobnix.sys.TempHolder;
import com.foobnix.webdav.WebDav;

import org.ebookdroid.common.settings.books.SharedBooks;
import org.greenrobot.eventbus.EventBus;

import java.text.DateFormat;
import java.util.Date;

/** Runs a WebDAV sync in the background, the way SynctornizatoinWorker runs the Google Drive one. */
public class WebDavSyncWorker extends MessageWorker {

    public WebDavSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    boolean doWorkInner() {
        final Context c = getApplicationContext();
        if (!WebDav.isEnabled(c)) {
            return false;
        }
        EventBus.getDefault().post(new MessageSync(MessageSync.STATE_VISIBLE));
        AppSP.get().syncTimeStatus = MessageSync.STATE_VISIBLE;
        log("\n WebDAV begin: " + DateFormat.getTimeInstance().format(new Date()));
        try {
            final WebDav.Summary summary = WebDav.syncNow(c);
            log("\n WebDAV " + summary);
            AppSP.get().syncTime = System.currentTimeMillis();
            AppSP.get().syncTimeStatus = summary.hasErrors() ? MessageSync.STATE_FAILE : MessageSync.STATE_SUCCESS;
            EventBus.getDefault().post(new MessageSync(AppSP.get().syncTimeStatus));
            if (summary.changedLocal()) {
                // progress, recent, tags... came from another device
                SharedBooks.cache.clear();
                TagData.restoreTags();
                TempHolder.get().listHash++;
                EventBus.getDefault().post(new UpdateAllFragments());
            }
        } catch (Exception e) {
            LOG.e(e);
            log("\n WebDAV error: " + e.getMessage());
            AppSP.get().syncTimeStatus = MessageSync.STATE_FAILE;
            EventBus.getDefault().post(new MessageSync(MessageSync.STATE_FAILE));
        }
        return true;
    }

    /** Into the sync log that the settings show (Dialogs.showSyncLOGDialog). */
    private static void log(String text) {
        synchronized (GFile.debugOut) {
            GFile.debugOut.append(text);
        }
    }
}
