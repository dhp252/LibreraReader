package com.foobnix.assist.recall;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.foobnix.android.utils.LOG;

import java.util.ArrayList;

/**
 * Talking instead of typing, with Android's built-in speech recognition
 * ({@link SpeechRecognizer}; no library, no audio kept by the app). Used by blank-page recall and
 * teach-back, only from a tap on their talk button (Assist.RECALL on).
 * <p>
 * Recognition stops by itself at a pause; while the reader still wants to talk it is started
 * again, and the pieces add up in a {@link VoiceText}. Where the device has no recognition
 * service, or the microphone permission is refused, {@link #available} / {@link #granted} say so
 * and the screens keep typing only. Main thread only.
 */
public final class VoiceInput implements RecognitionListener {

    /** The request code of the microphone permission (the readers ignore codes they do not know). */
    public static final int REQUEST = 0x7EC;
    /** A stop that brings no result within this time ends the talk anyway. */
    private static final long STOP_MS = 2500;
    /** Starts that fail at once, in a row, before giving up. */
    private static final int MAX_FAST_FAILS = 3;

    public interface Listener {
        /** Listening began, or ended (the text is then final). */
        void onVoiceState(boolean listening);

        /** Everything said so far in this talk, the guess in progress included. */
        void onVoiceText(String text);

        /** How loud the reader is now, 0..1. */
        void onVoiceLevel(float level);

        /** Recognition failed for good; what was heard before stays. */
        void onVoiceError(int error);
    }

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceText words = new VoiceText();
    private SpeechRecognizer recognizer;
    /** The reader still wants to talk. */
    private boolean wanted;
    private boolean listening;
    private long startedAt;
    private long listenAt;
    private int fastFails;
    private final Runnable forceEnd = this::end;

    public VoiceInput(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    /** Whether the device has a speech recognition service. */
    public static boolean available(Context c) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(c);
        } catch (Exception e) {
            LOG.e(e);
            return false;
        }
    }

    public static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Asks for the microphone; the answer shows in {@link #granted} when the screen has the focus again. */
    public static void ask(Activity a) {
        a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST);
    }

    public boolean isListening() {
        return listening;
    }

    /** Seconds since this talk began. */
    public int seconds() {
        return listening ? (int) ((System.currentTimeMillis() - startedAt) / 1000) : 0;
    }

    public String text() {
        return words.text();
    }

    /** Begins a talk from nothing. */
    public void start() {
        if (listening) {
            return;
        }
        words.clear();
        wanted = true;
        listening = true;
        fastFails = 0;
        startedAt = System.currentTimeMillis();
        handler.removeCallbacks(forceEnd);
        listener.onVoiceState(true);
        listen();
    }

    /** The reader is done talking: the last words still come, then {@link Listener#onVoiceState} false. */
    public void finish() {
        if (!listening) {
            return;
        }
        wanted = false;
        try {
            if (recognizer != null) {
                recognizer.stopListening();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        handler.removeCallbacks(forceEnd);
        handler.postDelayed(forceEnd, STOP_MS);
    }

    /** Stops and forgets what was said. */
    public void cancel() {
        wanted = false;
        handler.removeCallbacks(forceEnd);
        try {
            if (recognizer != null) {
                recognizer.cancel();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        words.clear();
        if (listening) {
            listening = false;
            listener.onVoiceState(false);
        }
    }

    /** The screen is going away. */
    public void destroy() {
        wanted = false;
        listening = false;
        handler.removeCallbacksAndMessages(null);
        try {
            if (recognizer != null) {
                recognizer.destroy();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        recognizer = null;
    }

    private void listen() {
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
                recognizer.setRecognitionListener(this);
            }
            final Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
            listenAt = System.currentTimeMillis();
            recognizer.startListening(i);
        } catch (Exception e) {
            LOG.e(e);
            fail(SpeechRecognizer.ERROR_CLIENT);
        }
    }

    private void end() {
        handler.removeCallbacks(forceEnd);
        wanted = false;
        if (!listening) {
            return;
        }
        words.commit();
        listening = false;
        listener.onVoiceText(words.text());
        listener.onVoiceState(false);
    }

    private void fail(int error) {
        final boolean was = listening;
        end();
        if (was) {
            listener.onVoiceError(error);
        }
    }

    private static String first(Bundle results) {
        final ArrayList<String> list = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null || list.isEmpty() || list.get(0) == null ? "" : list.get(0);
    }

    // ------------------------------------------------------------------ the recognizer

    @Override
    public void onReadyForSpeech(Bundle params) {
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (listening) {
            // the services report about -2 (silence) to 10 dB
            listener.onVoiceLevel(Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f)));
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onError(int error) {
        if (!listening) {
            return;
        }
        if (!wanted) {
            // after a stop: there was nothing more to hear
            end();
            return;
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            // a pause: keep listening while the reader holds on, unless starting fails every time
            words.commit();
            fastFails = System.currentTimeMillis() - listenAt < 700 ? fastFails + 1 : 0;
            if (fastFails < MAX_FAST_FAILS) {
                handler.postDelayed(() -> {
                    if (wanted && listening) {
                        listen();
                    }
                }, error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 400 : 100);
                return;
            }
        }
        fail(error);
    }

    @Override
    public void onResults(Bundle results) {
        if (!listening) {
            return;
        }
        final String piece = first(results);
        if (piece.isEmpty()) {
            words.commit();
        } else {
            words.add(piece);
        }
        listener.onVoiceText(words.text());
        fastFails = 0;
        if (wanted) {
            listen();
        } else {
            end();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (!listening) {
            return;
        }
        final String piece = first(partialResults);
        if (!piece.isEmpty()) {
            words.partial(piece);
            listener.onVoiceText(words.text());
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
}
