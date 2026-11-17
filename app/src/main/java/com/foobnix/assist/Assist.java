package com.foobnix.assist;

import com.foobnix.model.AppState;

/**
 * The assisted-reading features of the "Book That Reads You Back" canvas, one switch each
 * (Preferences > Assisted reading). All are off until the reader turns them on; off hides a
 * feature's buttons and stops its hooks, and its saved data stays (see {@link AssistStore}).
 */
public enum Assist {
    RECAP, PRE_QUESTIONS, XRAY, SKELETON, LENS, ZOOM, RECALL, PATH, UPDATED, USE_IT, REVIEW, EDITION,
    PICTURES, FICTION, READS_YOU;

    public boolean isOn() {
        return get(AppState.get(), this);
    }

    public void set(boolean on) {
        set(AppState.get(), this, on);
    }

    static boolean get(AppState s, Assist f) {
        switch (f) {
            case RECAP:
                return s.isAssistRecap;
            case PRE_QUESTIONS:
                return s.isAssistPreQuestions;
            case XRAY:
                return s.isAssistXray;
            case SKELETON:
                return s.isAssistSkeleton;
            case LENS:
                return s.isAssistLens;
            case ZOOM:
                return s.isAssistZoom;
            case RECALL:
                return s.isAssistRecall;
            case PATH:
                return s.isAssistPath;
            case UPDATED:
                return s.isAssistUpdated;
            case USE_IT:
                return s.isAssistUseIt;
            case REVIEW:
                return s.isAssistReview;
            case EDITION:
                return s.isAssistEdition;
            case PICTURES:
                return s.isAssistPictures;
            case FICTION:
                return s.isAssistFiction;
            case READS_YOU:
                return s.isAssistReadsYou;
            default:
                return false;
        }
    }

    static void set(AppState s, Assist f, boolean on) {
        switch (f) {
            case RECAP:
                s.isAssistRecap = on;
                break;
            case PRE_QUESTIONS:
                s.isAssistPreQuestions = on;
                break;
            case XRAY:
                s.isAssistXray = on;
                break;
            case SKELETON:
                s.isAssistSkeleton = on;
                break;
            case LENS:
                s.isAssistLens = on;
                break;
            case ZOOM:
                s.isAssistZoom = on;
                break;
            case RECALL:
                s.isAssistRecall = on;
                break;
            case PATH:
                s.isAssistPath = on;
                break;
            case UPDATED:
                s.isAssistUpdated = on;
                break;
            case USE_IT:
                s.isAssistUseIt = on;
                break;
            case REVIEW:
                s.isAssistReview = on;
                break;
            case EDITION:
                s.isAssistEdition = on;
                break;
            case PICTURES:
                s.isAssistPictures = on;
                break;
            case FICTION:
                s.isAssistFiction = on;
                break;
            case READS_YOU:
                s.isAssistReadsYou = on;
                break;
            default:
                break;
        }
    }
}
