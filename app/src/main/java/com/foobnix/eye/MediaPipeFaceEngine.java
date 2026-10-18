package com.foobnix.eye;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * MediaPipe Face Landmarker on the CPU, one face, in video mode (each call waits for its result,
 * on the camera thread). The model is read from the assets into a direct buffer, so asset
 * compression does not matter.
 */
final class MediaPipeFaceEngine implements FaceEngine {

    static final String MODEL = "eye/face_landmarker.task";

    private final FaceLandmarker landmarker;

    /** Callers catch Throwable: a device without MediaPipe's native library throws an Error here. */
    MediaPipeFaceEngine(Context context) throws Exception {
        final BaseOptions base = BaseOptions.builder()
                .setModelAssetBuffer(readModel(context))
                .setDelegate(Delegate.CPU)
                .build();
        final FaceLandmarker.FaceLandmarkerOptions options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(EyeRules.CONFIDENCE)
                .setMinFacePresenceConfidence(EyeRules.CONFIDENCE)
                .setMinTrackingConfidence(EyeRules.CONFIDENCE)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .build();
        landmarker = FaceLandmarker.createFromOptions(context, options);
    }

    private static ByteBuffer readModel(Context context) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(4 << 20);
        try (InputStream in = context.getAssets().open(MODEL)) {
            final byte[] chunk = new byte[64 << 10];
            int n;
            while ((n = in.read(chunk)) > 0) {
                bytes.write(chunk, 0, n);
            }
        }
        final byte[] all = bytes.toByteArray();
        final ByteBuffer buffer = ByteBuffer.allocateDirect(all.length).order(ByteOrder.nativeOrder());
        buffer.put(all);
        buffer.rewind();
        return buffer;
    }

    @Override
    public FaceFrame detect(Bitmap upright, long t) {
        final FaceFrame f = new FaceFrame();
        f.t = t;
        f.w = upright.getWidth();
        f.h = upright.getHeight();
        final MPImage image = new BitmapImageBuilder(upright).build();
        final FaceLandmarkerResult result = landmarker.detectForVideo(image, t);
        if (result == null || result.faceLandmarks().isEmpty()) {
            return f;
        }
        f.face = true;

        final List<NormalizedLandmark> points = result.faceLandmarks().get(0);
        final int n = points.size();
        final float[] x = new float[n];
        final float[] y = new float[n];
        final float[] nx = new float[n];
        final float[] ny = new float[n];
        for (int i = 0; i < n; i++) {
            final NormalizedLandmark p = points.get(i);
            nx[i] = p.x();
            ny[i] = p.y();
            x[i] = p.x() * f.w;
            y[i] = p.y() * f.h;
        }
        if (n > FaceSignals.L_UPPER) {
            FaceSignals.landmarks(f, x, y);
        }
        f.lx = nx;
        f.ly = ny;

        if (result.faceBlendshapes().isPresent() && !result.faceBlendshapes().get().isEmpty()) {
            final List<Category> shapes = result.faceBlendshapes().get().get(0);
            final List<String> names = new ArrayList<>(shapes.size());
            final List<Float> scores = new ArrayList<>(shapes.size());
            for (Category c : shapes) {
                names.add(c.categoryName());
                scores.add(c.score());
            }
            FaceSignals.blendshapes(f, names, scores);
        }
        if (result.facialTransformationMatrixes().isPresent() && !result.facialTransformationMatrixes().get().isEmpty()) {
            final float[] angles = HeadPose.angles(result.facialTransformationMatrixes().get().get(0));
            f.yaw = angles[0];
            f.pitch = angles[1];
            f.roll = angles[2];
        }
        return f;
    }

    @Override
    public void close() {
        landmarker.close();
    }
}
