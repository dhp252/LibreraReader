package com.foobnix.eye;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.eye.PresenceDetector.State;
import com.foobnix.model.AppSP;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.tts.TTSEngine;
import com.foobnix.tts.TTSNotification;
import com.foobnix.tts.TTSService;

/**
 * Eye mode (F-010): the front camera watches whether the reader looks at the page, for one book
 * session. The reader turns it on with the eye button; it ends when the book closes. One session
 * at a time, owned by the reader showing the book: a new reader of the same book (book ↔ scroll
 * mode) takes it over, as {@link com.foobnix.stats.ReadingTracker} does.
 * <p>
 * What it does with the presence state ({@link PresenceDetector}): away pauses auto-scroll or
 * page flipping (and read aloud if asked) and dims the screen after a while; looking again
 * resumes what Eye mode paused; while it sees you the screen stays on. Can't see leaves the reader
 * as it is without Eye mode. Open dialogs do not stop any of this (the page flipping dialog stays
 * open after Start).
 */
public final class EyeMode implements EyeCamera.Listener {

    /** The reader's side of Eye mode, one per reader activity. */
    public interface Host {
        Activity activity();

        String bookPath();

        /** Auto-scroll (scroll mode) or page flipping (book mode) is running. */
        boolean isAutoRunning();

        /** Stops it so that {@link #resumeAuto} can go on from there. */
        void pauseAuto();

        void resumeAuto();

        /** For eye-driven page turns (M2, M3), which must wait for dialogs to close. */
        boolean isDialogOpen();

        /** Starts the reader's own keep-screen-on time again from now (as a page turn does). */
        void restartScreenTimer();

        /** The status-bar icon; null hides it. */
        void showEyeState(State state);

        /** The footer button's look. */
        void showEyeButton(boolean on);

        /** A long blink or a gaze turn turns the page: book mode one page, scroll mode one screen. */
        void nextPageByEye();

        /** Scroll mode (auto-scroll by gaze) rather than book mode (gaze page turn). */
        boolean isScrollMode();

        /** Book mode shows two pages side by side. */
        boolean isTwoPages();

        /** The book reads right to left (the second of two pages is the left one). */
        boolean isRtl();

        /** How long the page on screen should take at the reader's speed, ms; -1 = not known. */
        long expectedPageMs();
    }

    public static final int REQ_CAMERA = 0xE7E;

    private static EyeMode current;
    /** The reader that asked for the camera permission, until the answer. */
    private static Host asking;

    private Host host;
    private final String path;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EyeCamera camera;
    private PresenceDetector presence = new PresenceDetector();
    private LongBlink blink = new LongBlink();
    /** The presence state when the current closure began. */
    private State stateAtClosure = State.CANT_SEE;
    private boolean resumed;
    private boolean pausedAuto;
    private boolean pausedTts;
    private boolean dimmed;
    private float brightnessBefore = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
    private boolean busyNoted;
    private TraceRecorder trace;
    private EyeDebugView debug;
    private long lastFrameAt;
    private float fps;
    private int tracedPage = -1;
    private String lastAction;
    private long lastActionAt;
    // calibrated gaze (M3)
    private final EyeGaze gaze = new EyeGaze();
    private Calibration calibrating;
    private EyeCalibrationView calibrationView;
    private androidx.activity.OnBackPressedCallback calibrationBack;
    private boolean pausedForCalibration;
    private EyeGazeView gazeView;
    /** Rotations the calibration was offered for in this session (once each). */
    private final java.util.Set<Integer> offered = new java.util.HashSet<>();
    private int shownPage = -1;
    private ScrollZones.Zone lastZone = ScrollZones.Zone.MIDDLE;

    private final Runnable dim = this::dim;

    private EyeMode(Host host) {
        this.host = host;
        this.path = host.bookPath();
    }

    // ---------------------------------------------------------------- the reader's calls

    /** This device has a front camera; without one the eye button stays hidden. */
    public static boolean isAvailable(Context c) {
        return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public static boolean isOn(Host host) {
        return current != null && current.host == host;
    }

    /** The eye button. */
    public static void toggle(Host host) {
        try {
            if (isOn(host)) {
                current.end();
                Toast.makeText(host.activity(), R.string.eye_off, Toast.LENGTH_SHORT).show();
                return;
            }
            final Activity a = host.activity();
            if (!AppSP.get().eyeExplained) {
                new AlertDialog.Builder(a)
                        .setTitle(R.string.eye_mode)
                        .setMessage(R.string.eye_intro)
                        .setPositiveButton(R.string.eye_turn_on, (d, w) -> {
                            AppSP.get().eyeExplained = true;
                            AppSP.get().save();
                            askOrStart(host);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
            askOrStart(host);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private static void askOrStart(Host host) {
        final Activity a = host.activity();
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            start(host);
        } else {
            asking = host;
            ActivityCompat.requestPermissions(a, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    /** From the reader's {@code onRequestPermissionsResult}. */
    public static void onPermissionResult(Activity a, int requestCode, int[] grantResults) {
        if (requestCode != REQ_CAMERA || asking == null || asking.activity() != a) {
            return;
        }
        final Host host = asking;
        asking = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            start(host);
        } else {
            new AlertDialog.Builder(a)
                    .setTitle(R.string.eye_mode)
                    .setMessage(R.string.eye_needs_camera)
                    .setPositiveButton(R.string.eye_app_settings, (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", a.getPackageName(), null)));
                        } catch (Exception e) {
                            LOG.e(e);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private static void start(Host host) {
        if (current != null) {
            current.end();
        }
        current = new EyeMode(host);
        current.begin();
    }

    /** The reader is on screen (also after rotation, and for a new reader of the same book). */
    public static void resumed(Host host) {
        try {
            if (current != null && !current.path.equals(host.bookPath())) {
                current.end();
            }
            if (current == null) {
                host.showEyeButton(false);
                host.showEyeState(null);
                return;
            }
            if (current.host != host) {
                current.takeOver(host);
            }
            current.resume();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public static void paused(Activity owner) {
        if (current != null && current.host.activity() == owner) {
            current.pause();
        }
    }

    /** The reader is gone: a finishing one ends the session (a re-created one keeps it). */
    public static void destroyed(Activity owner) {
        if (current != null && current.host.activity() == owner && owner.isFinishing()) {
            current.end();
        }
        if (asking != null && asking.activity() == owner) {
            asking = null;
        }
    }

    /** Any touch on the reader: the reader is there, so the screen comes back from dimmed. */
    public static void onTouch(Activity owner) {
        if (current == null || current.host.activity() != owner) {
            return;
        }
        // whoever touches the page is reading it: that is the reading pose now
        current.presence.reading(SystemClock.uptimeMillis());
        if (current.dimmed) {
            current.undim();
            if (current.presence.state() == State.AWAY) {
                current.scheduleDim();
            }
        }
    }

    /** A page is shown: recorded in the trace. */
    public static void pageShown(DocumentController dc) {
        if (current == null || dc == null || dc.getActivity() != current.host.activity()) {
            return;
        }
        final int page = dc.getCurentPageFirst1();
        if (page == current.shownPage) {
            return;
        }
        current.shownPage = page;
        final long now = SystemClock.uptimeMillis();
        if (!current.host.isScrollMode()) {
            current.gaze.pageShown(now, current.host.expectedPageMs());
        }
        if (current.trace != null && page != current.tracedPage) {
            current.tracedPage = page;
            current.trace.event(now, "page", "pg", page);
        }
    }

    /** A tap on the status icon: re-zero the gaze when it is calibrated and used, else the settings. */
    public static void iconTapped(Activity a) {
        if (current != null && current.host.activity() == a && current.gazeUsed() && current.gaze.hasModel()) {
            current.startCalibration(true);
        } else {
            showSettings(a);
        }
    }

    /** From the settings: calibrate the current orientation (Eye mode must be on). */
    static void calibrateNow(Activity a) {
        if (current == null || current.host.activity() != a || !current.resumed) {
            Toast.makeText(a, R.string.eye_turn_on_first, Toast.LENGTH_SHORT).show();
            return;
        }
        current.startCalibration(false);
    }

    /** For the settings: whether the current orientation has a good calibration. */
    static boolean isCalibratedNow(Activity a) {
        if (current != null && current.host.activity() == a) {
            if (current.gaze.rotation(current.rotationNow())) {
                current.blink.setProfile(current.gaze.profile());
            }
            return current.gaze.hasModel();
        }
        return false;
    }

    /**
     * For the reading stats: 1 = the reader is looking, 0 = away, -1 = not known (Eye mode off,
     * the reader off screen, can't see).
     */
    public static int lookState(Activity owner) {
        if (current == null || current.host.activity() != owner || !current.resumed || current.camera == null) {
            return -1;
        }
        switch (current.presence.state()) {
            case SEES:
                return 1;
            case AWAY:
                return 0;
            default:
                return -1;
        }
    }

    /** The settings dialog changed something. */
    static void settingsChanged() {
        if (current == null) {
            return;
        }
        current.applySettings();
        if (!AppSP.get().eyeScreen) {
            current.undim();
        }
    }

    /** Shares the newest trace; one still being recorded is written out first, or it ends mid-line. */
    static void shareTrace(Activity a) {
        if (current != null && current.trace != null) {
            current.trace.flush(() -> TraceRecorder.share(a));
        } else {
            TraceRecorder.share(a);
        }
    }

    /** Opens the settings from a reader (long press on the eye button, tap on the status icon). */
    public static void showSettings(Activity a) {
        EyeSettingsDialog.show(a);
    }

    // ---------------------------------------------------------------- the session

    private void begin() {
        camera = new EyeCamera(host.activity(), this);
        applySettings();
        host.showEyeButton(true);
        host.showEyeState(State.CANT_SEE);
        resume();
        Toast.makeText(host.activity(), R.string.eye_on, Toast.LENGTH_SHORT).show();
    }

    private void end() {
        if (current == this) {
            current = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (camera != null) {
            camera.release();
            camera = null;
        }
        undim();
        // the reader is here, turning Eye mode off: what it paused goes on
        giveBack();
        if (resumed) {
            host.restartScreenTimer();
        }
        resumed = false;
        endCalibration(true);
        removeDebug();
        removeGazeView();
        EyeScroll.set(1);
        if (trace != null) {
            trace.close();
            trace = null;
        }
        host.showEyeButton(false);
        host.showEyeState(null);
    }

    private void takeOver(Host next) {
        if (resumed) {
            pause();
        }
        endCalibration(true);
        removeDebug();
        removeGazeView();
        // the old reader's window goes with it: nothing left dimmed here
        handler.removeCallbacks(dim);
        dimmed = false;
        brightnessBefore = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        host = next;
        host.showEyeButton(true);
        applySettings();
    }

    private void resume() {
        if (resumed || camera == null) {
            return;
        }
        resumed = true;
        presence = new PresenceDetector();
        blink = new LongBlink();
        blink.setProfile(gaze.profile());
        host.showEyeState(State.CANT_SEE);
        camera.start(host.activity());
        if (trace != null) {
            trace.event(SystemClock.uptimeMillis(), "resume");
        }
    }

    private void pause() {
        if (!resumed) {
            return;
        }
        resumed = false;
        handler.removeCallbacks(dim);
        EyeScroll.set(1);
        if (camera != null) {
            camera.stop();
        }
        if (trace != null) {
            trace.event(SystemClock.uptimeMillis(), "pause");
        }
    }

    private void applySettings() {
        final AppSP sp = AppSP.get();
        if (camera != null) {
            camera.setPreview(sp.eyePreview);
            final int fps = sp.eyeLongBlink || sp.eyeGazeTurn || sp.eyeGazeScroll ? EyeRules.LONG_BLINK_FPS : EyeRules.PRESENCE_FPS;
            if (camera.fps() != fps) {
                camera.setFps(fps);
                if (camera.isRunning()) {
                    // the exposure range follows the rate: bind again
                    camera.stop();
                    camera.start(host.activity());
                }
            }
        }
        if (sp.eyePreview && gazeView == null) {
            addGazeView();
        } else if (!sp.eyePreview) {
            removeGazeView();
        }
        if (sp.eyePreview && debug == null) {
            addDebug();
        } else if (!sp.eyePreview) {
            removeDebug();
        }
        if (!gazeUsed()) {
            EyeScroll.set(1);
        }
        if (sp.eyeTrace && trace == null) {
            trace = TraceRecorder.start(host.activity());
        } else if (!sp.eyeTrace && trace != null) {
            trace.close();
            trace = null;
        }
    }

    private void addDebug() {
        try {
            final Activity a = host.activity();
            debug = new EyeDebugView(a);
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Dips.dpToPx(180), Dips.dpToPx(320),
                    Gravity.TOP | Gravity.END);
            lp.topMargin = Dips.dpToPx(56);
            lp.rightMargin = Dips.dpToPx(8);
            a.addContentView(debug, lp);
        } catch (Exception e) {
            LOG.e(e);
            debug = null;
        }
    }

    private void addGazeView() {
        try {
            gazeView = new EyeGazeView(host.activity());
            host.activity().addContentView(gazeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            LOG.e(e);
            gazeView = null;
        }
    }

    private void removeGazeView() {
        if (gazeView != null && gazeView.getParent() instanceof ViewGroup) {
            ((ViewGroup) gazeView.getParent()).removeView(gazeView);
        }
        gazeView = null;
    }

    private void removeDebug() {
        if (debug != null && debug.getParent() instanceof ViewGroup) {
            ((ViewGroup) debug.getParent()).removeView(debug);
        }
        debug = null;
    }

    @Override
    public void onFrame(FaceFrame f, Bitmap preview) {
        if (current != this || !resumed) {
            return;
        }
        if (lastFrameAt > 0 && f.t > lastFrameAt) {
            final float now = 1000f / (f.t - lastFrameAt);
            fps = fps == 0 ? now : fps + 0.2f * (now - fps);
        }
        lastFrameAt = f.t;

        if (gaze.rotation(rotationNow())) {
            blink.setProfile(gaze.profile());
        }
        final State before = presence.state();
        final boolean wasClosing = blink.isClosing();
        final State state = presence.update(f, blink.isClosed(f));
        final boolean longBlink = blink.update(f, state == State.SEES);
        if (!wasClosing && blink.isClosing()) {
            stateAtClosure = before;
        }
        final boolean closed = blink.isClosed(f);
        if (calibrating != null) {
            calibrating.frame(f, presence.isDark() && !presence.canTell());
        }
        final float[] g = gaze.predict(f, f.face && !closed && state == State.SEES && calibrating == null);
        f.gx = g[0];
        f.gy = g[1];
        if (trace != null) {
            trace.frame(f, state.name());
        }
        if (calibrating == null) {
            if (wasClosing && !blink.isClosing()) {
                closureEnded(longBlink);
            }
            gazeFrame(f, state);
        }
        if (debug != null) {
            debug.show(f, preview, state, presence, blink, fps, lastAction, lastAction == null ? 0 : f.t - lastActionAt);
        }
        if (state == State.SEES && AppSP.get().eyeScreen) {
            keepScreenOn();
        }
        if (state != before) {
            changed(before, state);
        }
    }

    @Override
    public void onCameraError(String why, boolean fatal) {
        if (current != this) {
            return;
        }
        LOG.d("EyeMode camera", why, fatal);
        final Activity a = host.activity();
        if (fatal) {
            Toast.makeText(a, a.getString(R.string.eye_cannot_start, why), Toast.LENGTH_LONG).show();
            end();
            return;
        }
        if (!busyNoted) {
            busyNoted = true;
            Toast.makeText(a, R.string.eye_camera_busy, Toast.LENGTH_LONG).show();
        }
        final State before = presence.state();
        final State state = presence.cameraBusy(SystemClock.uptimeMillis());
        if (state != before) {
            changed(before, state);
        }
    }

    private void changed(State before, State state) {
        host.showEyeState(state);
        if (trace != null) {
            trace.event(SystemClock.uptimeMillis(), "state", "from", before.name(), "to", state.name());
        }
        if (before == State.SEES) {
            // from now on the reader's own keep-screen-on time counts
            host.restartScreenTimer();
        }
        // a dialog open (the page flipping one stays open after Start) does not stop any of this:
        // only eye-driven page turns (M2, M3) will wait for dialogs to close
        final AppSP sp = AppSP.get();
        switch (state) {
            case AWAY:
                if (!sp.eyePauseAway) {
                    act("away · pausing is off in the settings");
                } else if (host.isAutoRunning()) {
                    host.pauseAuto();
                    pausedAuto = true;
                    act("paused auto-scroll / flipping");
                } else {
                    act("away · no auto-scroll or flipping running");
                }
                if (sp.eyePauseTts && TTSEngine.get().isPlaying()) {
                    tts(TTSNotification.TTS_PAUSE);
                    pausedTts = true;
                    act("paused read-aloud");
                }
                scheduleDim();
                break;
            case SEES:
                undim();
                giveBack();
                break;
            default:
                // can't see: nothing new; what was paused while away stays paused until you are seen
                break;
        }
    }

    // ---------------------------------------------------------------- calibrated gaze (M3)

    private int rotationNow() {
        try {
            return host.activity().getWindowManager().getDefaultDisplay().getRotation();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Whether a gaze feature applies to this reader: book mode's page turn or scroll mode's speed. */
    private boolean gazeUsed() {
        final AppSP sp = AppSP.get();
        return host.isScrollMode() ? sp.eyeGazeScroll : sp.eyeGazeTurn;
    }

    private void gazeFrame(FaceFrame f, State state) {
        final boolean used = gazeUsed();
        String label = "";
        if (used && gaze.hasModel()) {
            if (host.isScrollMode()) {
                final float factor = host.isAutoRunning() ? gaze.scrollFactor(lastGazeAt < 0 ? 0 : f.t - lastGazeAt) : 1;
                EyeScroll.set(factor);
                if (gaze.zone() != lastZone && host.isAutoRunning()) {
                    lastZone = gaze.zone();
                    if (trace != null) {
                        trace.event(f.t, "zone", "zone", lastZone.name());
                    }
                }
                label = String.format(java.util.Locale.US, "%s ×%.1f", gaze.zone().name(), factor);
            } else {
                final Boolean second = host.isTwoPages()
                        ? (host.isRtl() ? gaze.x() < 0.5f : gaze.x() > 0.5f) : null;
                if (gaze.bookTurn(f.t, second)) {
                    gazeTurn(state);
                }
                if (gaze.nudge(f.t, state == State.SEES)) {
                    gaze.saveModel();
                    if (trace != null) {
                        trace.event(f.t, "nudge", "offY", gaze.model().offY);
                    }
                }
                label = gaze.isArmed() ? "armed: look up to turn" : "";
            }
        } else {
            EyeScroll.set(1);
            if (used && state == State.SEES) {
                offerCalibration();
            }
        }
        lastGazeAt = f.t;
        if (gazeView != null) {
            gazeView.show(gaze.x(), gaze.y(), host.isScrollMode(), gaze.hasModel() ? label : "not calibrated");
        }
    }

    private long lastGazeAt = -1;

    private void gazeTurn(State state) {
        if (trace != null) {
            trace.event(SystemClock.uptimeMillis(), "gazeturn");
        }
        if (state != State.SEES) {
            act("gaze turn · not reading");
        } else if (host.isDialogOpen()) {
            act("gaze turn · a dialog is open");
        } else if (TTSEngine.get().isPlaying()) {
            act("gaze turn · read-aloud turns the pages");
        } else {
            host.nextPageByEye();
            tick();
            act("gaze · next page");
        }
    }

    /** Once per rotation and session: calibrate now or later. */
    private void offerCalibration() {
        final int r = gaze.rotation();
        if (offered.contains(r) || host.isDialogOpen() || calibrating != null) {
            return;
        }
        offered.add(r);
        final Activity a = host.activity();
        new AlertDialog.Builder(a)
                .setTitle(R.string.eye_calibrate_offer_title)
                .setMessage(host.isScrollMode() ? R.string.eye_calibrate_offer_scroll : R.string.eye_calibrate_offer_book)
                .setPositiveButton(R.string.eye_calibrate_now, (d, w) -> startCalibration(false))
                .setNegativeButton(R.string.eye_later, null)
                .show();
    }

    private void startCalibration(boolean rezero) {
        if (calibrating != null || !resumed) {
            return;
        }
        final Activity a = host.activity();
        try {
            if (host.isAutoRunning()) {
                host.pauseAuto();
                pausedForCalibration = true;
            }
            calibrating = rezero ? Calibration.rezero(SystemClock.uptimeMillis()) : Calibration.full(SystemClock.uptimeMillis());
            calibrationView = new EyeCalibrationView(a, calibrating, this::calibrationDone);
            a.addContentView(calibrationView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            if (a instanceof androidx.activity.ComponentActivity) {
                // a back gesture (predictive back sends no key) cancels instead of closing the book
                calibrationBack = new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (calibrating != null) {
                            calibrating.cancel();
                            calibrationDone(calibrating);
                        }
                    }
                };
                ((androidx.activity.ComponentActivity) a).getOnBackPressedDispatcher().addCallback(calibrationBack);
            }
            if (trace != null) {
                trace.event(SystemClock.uptimeMillis(), rezero ? "rezero" : "calibration", "start", calibrating.start(),
                        "settle", calibrating.settleMs(), "collect", calibrating.collectMs());
            }
        } catch (Exception e) {
            LOG.e(e);
            endCalibration(true);
        }
    }

    private void calibrationDone(Calibration c) {
        if (c != calibrating) {
            return;
        }
        final Activity a = host.activity();
        final Calibration.Result r = c.result();
        endCalibration(false);
        if (trace != null) {
            trace.event(SystemClock.uptimeMillis(), "calibrated", "result", r.name(), "dots", c.goodDots(),
                    "rms", c.model() == null ? -1 : c.model().rmsY());
        }
        int note;
        switch (r) {
            case OK:
                if (c.isRezero()) {
                    final float[] g = c.rezeroGaze();
                    if (gaze.model() != null && g != null) {
                        gaze.model().rezero(0.5f, 0.5f, g[0], g[1]);
                        gaze.saveModel();
                    }
                    note = R.string.eye_rezeroed;
                } else {
                    gaze.setModel(c.model());
                    blink.setProfile(c.profile());
                    Toast.makeText(a, a.getString(R.string.eye_cal_ok, Math.round(100 * c.model().rmsY())) + "\n"
                            + a.getString(eyesNote(c.profile())), Toast.LENGTH_LONG).show();
                    note = 0;
                }
                break;
            case POOR:
                // the gaze line is not good enough, but the reader's eyes still count for long blink
                if (c.model() != null) {
                    gaze.setModel(c.model());
                    blink.setProfile(c.profile());
                }
                note = R.string.eye_cal_poor;
                break;
            case TOO_DARK:
                note = R.string.eye_cal_dark;
                break;
            case CANCELLED:
                note = 0;
                break;
            default:
                note = R.string.eye_cal_no_face;
                break;
        }
        if (note != 0) {
            Toast.makeText(a, note, Toast.LENGTH_LONG).show();
        }
    }

    /** What the closing step gave, for the calibration's message. */
    private static int eyesNote(EyeProfile p) {
        if (p == null) {
            return R.string.eye_cal_eyes_failed;
        }
        return p.separable() ? R.string.eye_cal_eyes_ok : R.string.eye_cal_eyes_too_close;
    }

    /** Takes the calibration layer away and gives back what it paused. */
    private void endCalibration(boolean cancel) {
        if (calibrating != null && cancel) {
            calibrating.cancel();
        }
        calibrating = null;
        if (calibrationView != null && calibrationView.getParent() instanceof ViewGroup) {
            ((ViewGroup) calibrationView.getParent()).removeView(calibrationView);
        }
        calibrationView = null;
        if (calibrationBack != null) {
            calibrationBack.remove();
            calibrationBack = null;
        }
        if (pausedForCalibration) {
            pausedForCalibration = false;
            host.resumeAuto();
        }
    }

    /** A closure of the eyes ended: a long blink turns the page when nothing speaks against it. */
    private void closureEnded(boolean longBlink) {
        if (trace != null) {
            trace.event(SystemClock.uptimeMillis(), "blink", "ms", blink.lastMs(), "result", blink.last().name());
        }
        if (!longBlink) {
            return;
        }
        final AppSP sp = AppSP.get();
        if (trace != null && blink.isBlind()) {
            trace.event(SystemClock.uptimeMillis(), "blink", "blind", true);
        }
        if (!sp.eyeLongBlink) {
            act("long blink · turning is off in the settings");
        } else if (stateAtClosure != State.SEES) {
            act("long blink · not reading before it");
        } else if (host.isDialogOpen()) {
            act("long blink · a dialog is open");
        } else if (TTSEngine.get().isPlaying()) {
            act("long blink · read-aloud turns the pages");
        } else if (!presence.canTell()) {
            act("long blink · too dark to trust");
        } else {
            host.nextPageByEye();
            tick();
            act("long blink · next page");
        }
    }

    private void tick() {
        try {
            final android.view.View v = host.activity().getWindow().getDecorView();
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
            } else {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    /** Resumes what Eye mode paused, unless the reader restarted it already. */
    private void giveBack() {
        if (pausedAuto) {
            pausedAuto = false;
            if (!host.isAutoRunning()) {
                host.resumeAuto();
                act("resumed auto-scroll / flipping");
            }
        }
        if (pausedTts) {
            pausedTts = false;
            if (!TTSEngine.get().isPlaying()) {
                tts(TTSNotification.TTS_PLAY);
                act("resumed read-aloud");
            }
        }
    }

    /** What Eye mode last did, for the corner panel and the trace. */
    private void act(String what) {
        lastAction = what;
        lastActionAt = SystemClock.uptimeMillis();
        if (trace != null) {
            trace.event(lastActionAt, "action", "what", what);
        }
    }

    private void tts(String action) {
        try {
            final Activity a = host.activity();
            PendingIntent.getService(a, 0, new Intent(action, null, a, TTSService.class), PendingIntent.FLAG_IMMUTABLE).send();
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    private void keepScreenOn() {
        final Window w = host.activity().getWindow();
        if ((w.getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void scheduleDim() {
        handler.removeCallbacks(dim);
        if (AppSP.get().eyeScreen) {
            handler.postDelayed(dim, EyeRules.DIM_AFTER_MS);
        }
    }

    private void dim() {
        if (dimmed || presence.state() != State.AWAY || !AppSP.get().eyeScreen) {
            return;
        }
        final Window w = host.activity().getWindow();
        final WindowManager.LayoutParams lp = w.getAttributes();
        brightnessBefore = lp.screenBrightness;
        lp.screenBrightness = EyeRules.DIM_BRIGHTNESS;
        w.setAttributes(lp);
        dimmed = true;
        act("dimmed the screen");
    }

    private void undim() {
        handler.removeCallbacks(dim);
        if (!dimmed) {
            return;
        }
        dimmed = false;
        final Window w = host.activity().getWindow();
        final WindowManager.LayoutParams lp = w.getAttributes();
        lp.screenBrightness = brightnessBefore;
        w.setAttributes(lp);
    }
}
