/*
   Copyright 2012 Harri Smatt
   Modified 2026 for Librera: one-page mode only, driven by calls from the reader instead of its
   own touch listener, all mesh work on the GL thread, hosted in a TextureView.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.curl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.foobnix.pageturn.GLTextureView;

/**
 * Page curl over the reader. One page fills the view: a forward turn curls the shown page away
 * from the right edge over the next one; a backward turn brings the previous page in from the
 * left over the shown one. Calls come from the UI thread and are run on the GL thread.
 */
public class CurlView extends GLTextureView implements CurlRenderer.Observer {

    public interface Listener {
        /** On the UI thread, when a released or played turn has landed. */
        void onLanded(boolean turned);
    }

    private static final int CURL_NONE = 0;
    private static final int CURL_LEFT = 1;
    private static final int CURL_RIGHT = 2;

    private static final int SET_CURL_TO_LEFT = 1;
    private static final int SET_CURL_TO_RIGHT = 2;

    // GL thread state
    private boolean mAnimate = false;
    private long mAnimationDurationTime = 300;
    private final PointF mAnimationSource = new PointF();
    private long mAnimationStartTime;
    private final PointF mAnimationTarget = new PointF();
    private int mAnimationTargetEvent;
    private boolean mAnimationTurns;

    private final PointF mCurlDir = new PointF();
    private final PointF mCurlPos = new PointF();
    private int mCurlState = CURL_NONE;
    private final PointF mDragStartPos = new PointF();
    private final PointF mPointerPos = new PointF();

    // The page under the curl and the curling page.
    private final CurlMesh mPageStatic;
    private final CurlMesh mPageCurl;

    private final CurlRenderer mRenderer;
    private Listener mListener;

    public CurlView(Context ctx) {
        this(ctx, null);
    }

    public CurlView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        mRenderer = new CurlRenderer(this);
        setRenderer(mRenderer);
        mPageStatic = new CurlMesh(12);
        mPageCurl = new CurlMesh(12);
        mPageStatic.setFlipTexture(false);
        mPageCurl.setFlipTexture(false);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    // UI thread view of the state: from begin() or play() until clear().
    private volatile boolean mTurning;
    // UI thread: bumped by begin() and clear(), so a landing posted for an older turn is dropped
    private int mGeneration;
    // GL thread: the generation of the turn being drawn
    private int mAnimGeneration;

    public boolean isTurning() {
        return mTurning;
    }

    /**
     * Starts a turn with the page's edge at (x, y) in view pixels, as seen on screen: the
     * caller moves the edge by the finger's travel, so the page follows the finger instead of
     * jumping under it. {@code shown} and
     * {@code neighbor} are the page bitmaps (already mirrored when {@code mirror}); this view
     * takes them over. {@code backColor} tints the back of the curling page.
     */
    public void begin(final boolean forward, final Bitmap shown, final Bitmap neighbor,
                      final float x, final float y, final int backColor, final boolean mirror) {
        mTurning = true;
        final int generation = ++mGeneration;
        final float vx = mirror ? getWidth() - x : x;
        queueEvent(() -> {
            mAnimGeneration = generation;
            mCurlState = forward ? CURL_RIGHT : CURL_LEFT;
            mRenderer.setMirror(mirror);
            mRenderer.setBackgroundColor(backColor);
            mAnimate = false;
            RectF rect = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT);
            Bitmap curling = forward ? shown : neighbor;
            Bitmap under = forward ? neighbor : shown;

            setTextures(mPageStatic, under, backColor);
            setTextures(mPageCurl, curling, backColor);

            mRenderer.removeCurlMesh(mPageStatic);
            mRenderer.removeCurlMesh(mPageCurl);
            mPageStatic.setRect(rect);
            mPageStatic.reset();
            mPageCurl.setRect(rect);
            mPageCurl.reset();
            mRenderer.addCurlMesh(mPageStatic);
            mRenderer.addCurlMesh(mPageCurl);

            mPointerPos.set(vx, y);
            mRenderer.translate(mPointerPos);
            mDragStartPos.set(mPointerPos);
            if (mDragStartPos.y > rect.top) {
                mDragStartPos.y = rect.top;
            } else if (mDragStartPos.y < rect.bottom) {
                mDragStartPos.y = rect.bottom;
            }
            mDragStartPos.x = forward ? rect.right : rect.left;
            updateCurlPos(mPointerPos);
        });
        requestRender();
    }

    /** The page's edge moved to (x, y) in view pixels, as seen on screen. */
    public void drag(final float x, final float y, final boolean mirror) {
        final float vx = mirror ? getWidth() - x : x;
        queueEvent(() -> {
            if (mCurlState == CURL_NONE || mAnimate) {
                return;
            }
            mPointerPos.set(vx, y);
            mRenderer.translate(mPointerPos);
            updateCurlPos(mPointerPos);
        });
        requestRender();
    }

    /** The finger left: the turn completes ({@code turn}) or falls back, then lands. */
    public void release(final boolean turn) {
        queueEvent(() -> animateTo(turn, -1));
        requestRender();
    }

    /** A turn without a finger (tap, key): starts at the lower corner and lands in {@code ms}. */
    public void play(final boolean forward, final Bitmap shown, final Bitmap neighbor,
                     final int backColor, final boolean mirror, final long ms) {
        float x = forward ? getWidth() * 0.97f : getWidth() * 0.03f;
        float y = getHeight() * 0.88f;
        if (mirror) {
            x = getWidth() - x;
        }
        begin(forward, shown, neighbor, x, y, backColor, mirror);
        queueEvent(() -> animateTo(true, ms));
        requestRender();
    }

    /** Drops the pages: the view becomes transparent again. */
    public void clear() {
        mTurning = false;
        ++mGeneration;
        queueEvent(() -> {
            mCurlState = CURL_NONE;
            mAnimate = false;
            mRenderer.removeCurlMesh(mPageStatic);
            mRenderer.removeCurlMesh(mPageCurl);
            mPageStatic.getTexturePage().reset();
            mPageCurl.getTexturePage().reset();
        });
        requestRender();
    }

    private void setTextures(CurlMesh mesh, Bitmap bitmap, int backColor) {
        CurlPage page = mesh.getTexturePage();
        page.reset();
        page.setTexture(bitmap, CurlPage.SIDE_BOTH);
        page.setColor(backColor, CurlPage.SIDE_BACK);
    }

    // GL thread
    private void animateTo(boolean turn, long ms) {
        if (mCurlState == CURL_NONE) {
            return;
        }
        RectF rect = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT);
        RectF leftRect = mRenderer.getPageRect(CurlRenderer.PAGE_LEFT);
        mAnimationSource.set(mPointerPos);
        mAnimationTarget.set(mDragStartPos);
        boolean toLeft = (mCurlState == CURL_RIGHT) == turn;
        if (toLeft) {
            mAnimationTarget.x = mCurlState == CURL_RIGHT ? leftRect.left : rect.left;
            mAnimationTargetEvent = SET_CURL_TO_LEFT;
        } else {
            mAnimationTarget.x = rect.right;
            mAnimationTargetEvent = SET_CURL_TO_RIGHT;
        }
        if (ms > 0) {
            mAnimationDurationTime = ms;
        } else {
            // the farther the page still has to go, the longer, within a calm range
            float span = rect.width() * 2;
            float left = Math.abs(mAnimationTarget.x - mAnimationSource.x) / span;
            mAnimationDurationTime = (long) Math.max(180, Math.min(420, 140 + 360 * left));
        }
        mAnimationTurns = turn;
        mAnimationStartTime = System.currentTimeMillis();
        mAnimate = true;
    }

    @Override
    public void onDrawFrame() {
        if (!mAnimate) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime >= mAnimationStartTime + mAnimationDurationTime) {
            mAnimate = false;
            // Leave the page that is now shown flat, so the frame matches the reader until the
            // reader has moved and clear() makes this view transparent.
            mRenderer.removeCurlMesh(mPageCurl);
            // the curling page is the one left in view when a forward turn fell back or a
            // backward turn completed
            if ((mCurlState == CURL_RIGHT) != mAnimationTurns) {
                mPageCurl.reset();
                mRenderer.addCurlMesh(mPageCurl);
            }
            final boolean turned = mAnimationTurns;
            final int generation = mAnimGeneration;
            post(() -> {
                if (generation == mGeneration && mListener != null) {
                    mListener.onLanded(turned);
                }
            });
        } else {
            mPointerPos.set(mAnimationSource);
            float t = 1f - ((float) (currentTime - mAnimationStartTime) / mAnimationDurationTime);
            t = 1f - (t * t * t * (3 - 2 * t));
            mPointerPos.x += (mAnimationTarget.x - mAnimationSource.x) * t;
            mPointerPos.y += (mAnimationTarget.y - mAnimationSource.y) * t;
            updateCurlPos(mPointerPos);
        }
        requestRender();
    }

    @Override
    public void onPageSizeChanged(int width, int height) {
    }

    @Override
    public void onSurfaceCreated() {
        mPageStatic.resetTexture();
        mPageCurl.resetTexture();
    }

    private void setCurlPos(PointF curlPos, PointF curlDir, double radius) {
        // First reposition curl so that page doesn't 'rip off' from book.
        RectF pageRect = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT);
        if (curlPos.x >= pageRect.right) {
            mPageCurl.reset();
            return;
        }
        if (curlPos.x < pageRect.left) {
            curlPos.x = pageRect.left;
        }
        if (curlDir.y != 0) {
            float diffX = curlPos.x - pageRect.left;
            float leftY = curlPos.y + (diffX * curlDir.x / curlDir.y);
            if (curlDir.y < 0 && leftY < pageRect.top) {
                curlDir.x = curlPos.y - pageRect.top;
                curlDir.y = pageRect.left - curlPos.x;
            } else if (curlDir.y > 0 && leftY > pageRect.bottom) {
                curlDir.x = pageRect.bottom - curlPos.y;
                curlDir.y = curlPos.x - pageRect.left;
            }
        }

        // Finally normalize direction vector and do rendering.
        double dist = Math.sqrt(curlDir.x * curlDir.x + curlDir.y * curlDir.y);
        if (dist != 0) {
            curlDir.x /= dist;
            curlDir.y /= dist;
            mPageCurl.curl(curlPos, curlDir, radius);
        } else {
            mPageCurl.reset();
        }
    }

    private void updateCurlPos(PointF pointerPos) {
        RectF pageRect = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT);
        // Default curl radius (harism's, with the fixed pressure of 0.8 it used without one).
        double radius = pageRect.width() / 3 * 0.2f;
        mCurlPos.set(pointerPos);

        if (mCurlState == CURL_RIGHT) {
            mCurlDir.x = mCurlPos.x - mDragStartPos.x;
            mCurlDir.y = mCurlPos.y - mDragStartPos.y;
            float dist = (float) Math.sqrt(mCurlDir.x * mCurlDir.x + mCurlDir.y * mCurlDir.y);

            // Adjust curl radius so that if page is dragged far enough on
            // opposite side, radius gets closer to zero.
            float pageWidth = pageRect.width();
            double curlLen = radius * Math.PI;
            if (dist > (pageWidth * 2) - curlLen) {
                curlLen = Math.max((pageWidth * 2) - dist, 0f);
                radius = curlLen / Math.PI;
            }

            if (dist >= curlLen) {
                double translate = (dist - curlLen) / 2;
                radius = Math.max(Math.min(mCurlPos.x - pageRect.left, radius), 0f);
                mCurlPos.y -= mCurlDir.y * translate / dist;
            } else {
                double angle = Math.PI * Math.sqrt(dist / curlLen);
                double translate = radius * Math.sin(angle);
                mCurlPos.x += mCurlDir.x * translate / dist;
                mCurlPos.y += mCurlDir.y * translate / dist;
            }
        } else if (mCurlState == CURL_LEFT) {
            // Adjust radius regarding how close to page edge we are.
            radius = Math.max(Math.min(mCurlPos.x - pageRect.left, radius), 0f);
            mCurlPos.x -= Math.min(pageRect.right - mCurlPos.x, radius);
            mCurlDir.x = mCurlPos.x + mDragStartPos.x;
            mCurlDir.y = mCurlPos.y - mDragStartPos.y;
        } else {
            return;
        }

        setCurlPos(mCurlPos, mCurlDir, radius);
    }
}
