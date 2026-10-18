package com.foobnix.eye;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.foobnix.android.utils.LOG;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The front camera feeding the face engine: CameraX image analysis at about 640×480 RGBA,
 * frames dropped down to the wanted rate, each turned upright and analysed on one camera
 * thread. Results come back on the main thread. Its own lifecycle owner: the camera runs exactly
 * between {@link #start} and {@link #stop}, whatever the activity does.
 */
final class EyeCamera {

    interface Listener {
        /** @param preview a small upright copy of the frame while asked for, else null */
        void onFrame(FaceFrame frame, Bitmap preview);

        /** The camera is busy or failed ({@code fatal}: the face engine cannot run on this device). */
        void onCameraError(String why, boolean fatal);
    }

    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "EyeCamera"));
    private final Owner owner = new Owner();

    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private LiveData<CameraState> cameraState;
    private final Observer<CameraState> stateObserver = this::onCameraState;
    private DisplayManager.DisplayListener displayListener;
    private Display display;
    private boolean running;
    private boolean released;

    // camera thread
    private FaceEngine engine;
    private boolean engineFailed;
    private long lastFrame;
    private Bitmap frameBitmap;
    private Bitmap uprightBitmap;
    private final Matrix turn = new Matrix();

    private volatile int fps = EyeRules.PRESENCE_FPS;
    private volatile boolean preview;

    EyeCamera(Context context, Listener listener) {
        this.app = context.getApplicationContext();
        this.listener = listener;
        worker.execute(this::createEngine);
    }

    void setPreview(boolean on) {
        preview = on;
    }

    /** Main thread. */
    void start(Activity activity) {
        if (running || released) {
            return;
        }
        running = true;
        display = ((WindowManager) activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(app);
        future.addListener(() -> {
            try {
                provider = future.get();
                if (running) {
                    bind();
                }
            } catch (Exception e) {
                LOG.e(e);
                listener.onCameraError(String.valueOf(e.getMessage()), false);
            }
        }, ContextCompat.getMainExecutor(app));
    }

    /** Main thread. The camera closes; the engine stays loaded for the next start. */
    void stop() {
        running = false;
        if (cameraState != null) {
            cameraState.removeObserver(stateObserver);
            cameraState = null;
        }
        if (provider != null && analysis != null) {
            try {
                provider.unbind(analysis);
            } catch (Exception e) {
                LOG.e(e);
            }
            analysis.clearAnalyzer();
            analysis = null;
        }
        if (displayListener != null) {
            ((DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE)).unregisterDisplayListener(displayListener);
            displayListener = null;
        }
    }

    /** Main thread. */
    void release() {
        stop();
        released = true;
        owner.registry.setCurrentState(Lifecycle.State.DESTROYED);
        worker.execute(() -> {
            if (engine != null) {
                try {
                    engine.close();
                } catch (Throwable t) {
                    LOG.e(t);
                }
                engine = null;
            }
        });
        worker.shutdown();
    }

    private void createEngine() {
        try {
            engine = new MediaPipeFaceEngine(app);
        } catch (Throwable t) {
            LOG.e(t);
            engineFailed = true;
            final String why = t.getClass().getSimpleName() + ": " + t.getMessage();
            main.post(() -> listener.onCameraError(why, true));
        }
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void bind() {
        final ImageAnalysis.Builder builder = new ImageAnalysis.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(EyeRules.FRAME_W, EyeRules.FRAME_H),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetRotation(rotation());
        final Range<Integer> range = fpsRange();
        if (range != null) {
            new Camera2Interop.Extender<>(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range);
        }
        analysis = builder.build();
        analysis.setAnalyzer(worker, this::analyze);
        try {
            final Camera camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis);
            cameraState = camera.getCameraInfo().getCameraState();
            cameraState.observe(owner, stateObserver);
            owner.registry.setCurrentState(Lifecycle.State.RESUMED);
        } catch (Exception e) {
            LOG.e(e);
            listener.onCameraError(String.valueOf(e.getMessage()), false);
            return;
        }
        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (analysis != null && display != null && display.getDisplayId() == displayId) {
                    analysis.setTargetRotation(rotation());
                }
            }
        };
        ((DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE)).registerDisplayListener(displayListener, main);
    }

    private int rotation() {
        return display != null ? display.getRotation() : Surface.ROTATION_0;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private Range<Integer> fpsRange() {
        try {
            final List<CameraInfo> front = CameraSelector.DEFAULT_FRONT_CAMERA.filter(provider.getAvailableCameraInfos());
            if (front.isEmpty()) {
                return null;
            }
            final Range<Integer>[] ranges = Camera2CameraInfo.from(front.get(0))
                    .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (ranges == null || ranges.length == 0) {
                return null;
            }
            final int[] lows = new int[ranges.length];
            final int[] highs = new int[ranges.length];
            for (int i = 0; i < ranges.length; i++) {
                lows[i] = ranges[i].getLower();
                highs[i] = ranges[i].getUpper();
            }
            final int i = CameraMath.pickFpsRange(lows, highs, fps);
            LOG.d("EyeCamera fps range", i >= 0 ? ranges[i] : "none");
            return i >= 0 ? ranges[i] : null;
        } catch (Exception e) {
            LOG.e(e);
            return null;
        }
    }

    private void onCameraState(CameraState state) {
        final CameraState.StateError error = state.getError();
        if (error != null && running) {
            listener.onCameraError("camera error " + error.getCode(), false);
        }
    }

    // ---------------------------------------------------------------- camera thread

    private void analyze(@NonNull ImageProxy image) {
        try {
            if (engine == null || engineFailed) {
                return;
            }
            final long now = SystemClock.uptimeMillis();
            if (now - lastFrame < 1000 / Math.max(1, fps) - 15) {
                return;
            }
            lastFrame = now;

            final ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            final ByteBuffer pixels = plane.getBuffer();
            final int w = image.getWidth();
            final int h = image.getHeight();
            final float luma = Luma.mean(pixels, w, h, plane.getRowStride(), plane.getPixelStride());
            final int rot = image.getImageInfo().getRotationDegrees();

            final Bitmap upright = upright(bitmapOf(image, plane, w, h), w, h, rot);
            final FaceFrame f = engine.detect(upright, now);
            f.luma = luma;
            f.rot = rot;
            final Bitmap small = preview
                    ? Bitmap.createScaledBitmap(upright, EyeRules.PREVIEW_W, EyeRules.PREVIEW_W * upright.getHeight() / upright.getWidth(), true)
                    : null;
            main.post(() -> {
                if (running) {
                    listener.onFrame(f, small);
                }
            });
        } catch (Throwable t) {
            LOG.e(t);
        } finally {
            image.close();
        }
    }

    private Bitmap bitmapOf(ImageProxy image, ImageProxy.PlaneProxy plane, int w, int h) {
        if (plane.getPixelStride() == 4 && plane.getRowStride() == w * 4) {
            if (frameBitmap == null || frameBitmap.getWidth() != w || frameBitmap.getHeight() != h) {
                frameBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            final ByteBuffer pixels = plane.getBuffer();
            pixels.rewind();
            frameBitmap.copyPixelsFromBuffer(pixels);
            return frameBitmap;
        }
        return image.toBitmap();
    }

    /** The frame turned by {@code rot} degrees into a bitmap kept from frame to frame. */
    private Bitmap upright(Bitmap source, int w, int h, int rot) {
        if (rot % 360 == 0) {
            return source;
        }
        final int[] size = CameraMath.upright(w, h, rot);
        if (uprightBitmap == null || uprightBitmap.getWidth() != size[0] || uprightBitmap.getHeight() != size[1]) {
            uprightBitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        }
        turn.reset();
        turn.postRotate(rot);
        switch (rot) {
            case 90:
                turn.postTranslate(h, 0);
                break;
            case 180:
                turn.postTranslate(w, h);
                break;
            default:
                turn.postTranslate(0, w);
                break;
        }
        new Canvas(uprightBitmap).drawBitmap(source, turn, null);
        return uprightBitmap;
    }

    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry registry = new LifecycleRegistry(this);

        Owner() {
            registry.setCurrentState(Lifecycle.State.CREATED);
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }
}
