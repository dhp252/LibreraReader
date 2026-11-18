package com.foobnix.claude;

import android.content.Context;

import com.foobnix.ai.AiSettings;
import com.foobnix.model.AppProfile;

import java.io.File;

/**
 * Working with an outside Claude through the WebDAV folder (AI settings > Work with Claude through
 * WebDAV): the app writes the skill ({@link ClaudeSkill}) and a desk per book
 * ({@link ClaudeDesk}); Claude writes answers ({@link ClaudeAnswers}) that features take instead of
 * asking the API. All of it only while the switch is on.
 */
public final class Claude {

    private Claude() {
    }

    public static boolean isOn(Context c) {
        return c != null && AiSettings.load(c).claudeDesk;
    }

    /** The desk of a book in the current profile. */
    public static File desk(String bookName) {
        return ClaudeAnswers.desk(AppProfile.SYNC_FOLDER_PROFILE, bookName);
    }

    /** The answer waiting for this feature and chapter of the book, or null (also when the switch is off). */
    public static ClaudeAnswers.Answer take(Context c, String bookName, ClaudeFeature f, String chapter) {
        if (!isOn(c) || AppProfile.SYNC_FOLDER_PROFILE == null || chapter == null) {
            return null;
        }
        return ClaudeAnswers.find(desk(bookName), f.id, chapter, f.format);
    }

    /** Whether an answer waits for this feature and chapter (the feature then needs no API key). */
    public static boolean waiting(Context c, String bookName, ClaudeFeature f, String chapter) {
        return isOn(c) && AppProfile.SYNC_FOLDER_PROFILE != null && chapter != null
                && ClaudeAnswers.has(desk(bookName), f.id, chapter, f.format);
    }
}
