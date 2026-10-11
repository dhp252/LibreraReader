package com.foobnix.pageturn;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.TextureView;

import com.foobnix.android.utils.LOG;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/**
 * A TextureView that runs a {@link GLSurfaceView.Renderer} on its own GL thread, rendering only
 * on request. Unlike GLSurfaceView it composes like any view: it can lie over the reader, below
 * the toolbars, and show the reader through until its first frame (design D7). GLES 1.x through
 * EGL10, so harism's GL10 code runs unchanged.
 */
public class GLTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    public interface OnGlErrorListener {
        void onGlError();
    }

    private GLSurfaceView.Renderer renderer;
    private GLThread thread;
    private OnGlErrorListener onGlError;

    public GLTextureView(Context context) {
        super(context);
        init();
    }

    public GLTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    public void setRenderer(GLSurfaceView.Renderer renderer) {
        this.renderer = renderer;
    }

    public void setOnGlErrorListener(OnGlErrorListener onGlError) {
        this.onGlError = onGlError;
    }

    /** True once the GL thread drew on a live surface. */
    public boolean isReady() {
        GLThread t = thread;
        return t != null && t.ready;
    }

    public void requestRender() {
        GLThread t = thread;
        if (t != null) {
            t.requestRender();
        }
    }

    public void queueEvent(Runnable r) {
        GLThread t = thread;
        if (t != null) {
            t.queueEvent(r);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        stopThread();
        thread = new GLThread(surface, renderer);
        thread.onSizeChanged(width, height);
        thread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        GLThread t = thread;
        if (t != null) {
            t.onSizeChanged(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        stopThread();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void stopThread() {
        GLThread t = thread;
        thread = null;
        if (t != null) {
            t.finish();
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void reportError() {
        post(() -> {
            if (onGlError != null) {
                onGlError.onGlError();
            }
        });
    }

    private class GLThread extends Thread {
        private final SurfaceTexture surfaceTexture;
        private final GLSurfaceView.Renderer renderer;
        private final ArrayList<Runnable> events = new ArrayList<>();
        private boolean renderRequested = true;
        private boolean sizeChanged;
        private boolean finished;
        private int width, height;
        volatile boolean ready;

        private EGL10 egl;
        private EGLDisplay display;
        private EGLContext context;
        private EGLSurface eglSurface;

        GLThread(SurfaceTexture surfaceTexture, GLSurfaceView.Renderer renderer) {
            super("GLTextureView");
            this.surfaceTexture = surfaceTexture;
            this.renderer = renderer;
        }

        synchronized void onSizeChanged(int w, int h) {
            width = w;
            height = h;
            sizeChanged = true;
            renderRequested = true;
            notifyAll();
        }

        synchronized void requestRender() {
            renderRequested = true;
            notifyAll();
        }

        synchronized void queueEvent(Runnable r) {
            events.add(r);
            notifyAll();
        }

        synchronized void finish() {
            finished = true;
            notifyAll();
        }

        @Override
        public void run() {
            try {
                if (!initGl()) {
                    reportError();
                    return;
                }
                GL10 gl = (GL10) context.getGL();
                renderer.onSurfaceCreated(gl, null);
                loop(gl);
            } catch (Throwable e) {
                LOG.e(e);
                reportError();
            } finally {
                ready = false;
                releaseGl();
            }
        }

        private void loop(GL10 gl) throws InterruptedException {
            while (true) {
                ArrayList<Runnable> run;
                boolean resize;
                int w, h;
                synchronized (this) {
                    while (!finished && !renderRequested && events.isEmpty()) {
                        wait();
                    }
                    if (finished) {
                        return;
                    }
                    run = new ArrayList<>(events);
                    events.clear();
                    resize = sizeChanged;
                    sizeChanged = false;
                    renderRequested = false;
                    w = width;
                    h = height;
                }
                for (Runnable r : run) {
                    r.run();
                }
                if (resize) {
                    renderer.onSurfaceChanged(gl, w, h);
                }
                renderer.onDrawFrame(gl);
                if (!egl.eglSwapBuffers(display, eglSurface)) {
                    int error = egl.eglGetError();
                    LOG.d("GLTextureView swap failed", error);
                    if (error == EGL11.EGL_CONTEXT_LOST) {
                        reportError();
                    }
                    return;
                }
                ready = true;
            }
        }

        private boolean initGl() {
            egl = (EGL10) EGLContext.getEGL();
            display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (display == EGL10.EGL_NO_DISPLAY || !egl.eglInitialize(display, new int[2])) {
                return false;
            }
            int[] spec = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!egl.eglChooseConfig(display, spec, configs, 1, count) || count[0] == 0) {
                return false;
            }
            context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, null);
            if (context == null || context == EGL10.EGL_NO_CONTEXT) {
                return false;
            }
            eglSurface = egl.eglCreateWindowSurface(display, configs[0], surfaceTexture, null);
            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                return false;
            }
            return egl.eglMakeCurrent(display, eglSurface, eglSurface, context);
        }

        private void releaseGl() {
            if (egl == null || display == null) {
                return;
            }
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            if (eglSurface != null && eglSurface != EGL10.EGL_NO_SURFACE) {
                egl.eglDestroySurface(display, eglSurface);
            }
            if (context != null && context != EGL10.EGL_NO_CONTEXT) {
                egl.eglDestroyContext(display, context);
            }
            egl.eglTerminate(display);
        }
    }
}
