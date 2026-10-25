package com.foobnix.pdf.info.wrapper;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.OrientationEventListener;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.foobnix.model.AppState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * "Portrait (auto)": portrait or upside-down portrait, as the device is held. Android's own
 * sensorPortrait never turns a phone upside down, so the sensor is watched here and the activity
 * is switched between the two fixed portraits while it is in front.
 */
public final class AutoPortrait extends OrientationEventListener implements DefaultLifecycleObserver {

    /** How far (degrees) from exactly upside down the device may be held and still flip. */
    static final int FLIP_TOLERANCE = 40;

    private static final Map<Activity, AutoPortrait> ACTIVE = new WeakHashMap<>();

    private final Activity activity;

    private AutoPortrait(Activity activity) {
        super(activity);
        this.activity = activity;
    }

    /** Applies the setting to the activity; for "Portrait (auto)" starts watching the sensor. */
    static void apply(Activity a, int orientation) {
        AutoPortrait watcher = ACTIVE.get(a);
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
            if (watcher != null) {
                watcher.disable();
            }
            a.setRequestedOrientation(orientation);
            return;
        }
        int requested = a.getRequestedOrientation();
        if (requested != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && requested != ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        if (watcher != null) {
            watcher.onResume((LifecycleOwner) a);
            return;
        }
        if (!(a instanceof LifecycleOwner)) {
            return;
        }
        watcher = new AutoPortrait(a);
        ACTIVE.put(a, watcher);
        ((LifecycleOwner) a).getLifecycle().addObserver(watcher);
    }

    /**
     * Whether a device held at {@code degrees} (clockwise from its natural orientation, as
     * {@link OrientationEventListener} reports) is upside down to a screen at {@code displayRotation}
     * (a {@code Surface.ROTATION_*} value).
     */
    static boolean isUpsideDown(int degrees, int displayRotation) {
        if (degrees == ORIENTATION_UNKNOWN) {
            return false;
        }
        int upright = (360 - displayRotation * 90) % 360;
        int distance = Math.abs(degrees - (upright + 180) % 360);
        return Math.min(distance, 360 - distance) <= FLIP_TOLERANCE;
    }

    @Override
    public void onOrientationChanged(int degrees) {
        if (AppState.get().orientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        if (isUpsideDown(degrees, rotation)) {
            activity.setRequestedOrientation(activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (AppState.get().orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT && canDetectOrientation()) {
            enable();
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        disable();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        disable();
        owner.getLifecycle().removeObserver(this);
        ACTIVE.remove(activity);
    }
}
