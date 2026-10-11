/*
   Copyright 2012 Harri Smatt
   Modified 2026 for Librera: see the "Librera:" comments.

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

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;

/**
 * Actual renderer class.
 * 
 * @author harism
 */
public class CurlRenderer implements GLSurfaceView.Renderer {

	// Constant for requesting left page rect.
	public static final int PAGE_LEFT = 1;
	// Constant for requesting right page rect.
	public static final int PAGE_RIGHT = 2;
	// Constants for changing view mode.
	public static final int SHOW_ONE_PAGE = 1;
	public static final int SHOW_TWO_PAGES = 2;
	// Set to true for checking quickly how perspective projection looks.
	private static final boolean USE_PERSPECTIVE_PROJECTION = false;
	// Background fill color.
	private int mBackgroundColor;
	// Curl meshes used for static and dynamic rendering.
	private Vector<CurlMesh> mCurlMeshes;
	private RectF mMargins = new RectF();
	private CurlRenderer.Observer mObserver;
	// Page rectangles.
	private RectF mPageRectLeft;
	private RectF mPageRectRight;
	// View mode.
	private int mViewMode = SHOW_ONE_PAGE;
	// Screen size.
	private int mViewportWidth, mViewportHeight;
	// Rect for render area.
	private RectF mViewRect = new RectF();

	/**
	 * Basic constructor.
	 */
	public CurlRenderer(CurlRenderer.Observer observer) {
		mObserver = observer;
		mCurlMeshes = new Vector<CurlMesh>();
		mPageRectLeft = new RectF();
		mPageRectRight = new RectF();
	}

	/**
	 * Adds CurlMesh to this renderer.
	 */
	public synchronized void addCurlMesh(CurlMesh mesh) {
		removeCurlMesh(mesh);
		mCurlMeshes.add(mesh);
	}

	/**
	 * Returns rect reserved for left or right page. Value page should be
	 * PAGE_LEFT or PAGE_RIGHT.
	 */
	public RectF getPageRect(int page) {
		if (page == PAGE_LEFT) {
			return mPageRectLeft;
		} else if (page == PAGE_RIGHT) {
			return mPageRectRight;
		}
		return null;
	}

	@Override
	public synchronized void onDrawFrame(GL10 gl) {

		mObserver.onDrawFrame();

		if (mMirrorApplied != mMirror) {
			applyProjection(gl);
		}

		// Librera: the view lies over the reader. With no page on it, it is cleared to
		// transparent so the reader shows through; with pages, alpha is written once by the
		// clear (opaque) and masked while drawing, since the shadows' blending would otherwise
		// lower it and let the reader show through the shadows.
		gl.glColorMask(true, true, true, true);
		if (mCurlMeshes.isEmpty()) {
			gl.glClearColor(0f, 0f, 0f, 0f);
			gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
			return;
		}
		gl.glClearColor(Color.red(mBackgroundColor) / 255f,
				Color.green(mBackgroundColor) / 255f,
				Color.blue(mBackgroundColor) / 255f,
				1f);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
		gl.glColorMask(true, true, true, false);
		gl.glLoadIdentity();

		if (USE_PERSPECTIVE_PROJECTION) {
			gl.glTranslatef(0, 0, -6f);
		}

		for (int i = 0; i < mCurlMeshes.size(); ++i) {
			mCurlMeshes.get(i).onDrawFrame(gl);
		}
	}

	// Librera: right-to-left books turn the mirror way. The projection is flipped horizontally;
	// the caller mirrors the touch x and the page bitmaps, so the text still reads correctly.
	private volatile boolean mMirror;
	private boolean mMirrorApplied;

	public void setMirror(boolean mirror) {
		mMirror = mirror;
	}

	private void applyProjection(GL10 gl) {
		mMirrorApplied = mMirror;
		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		if (USE_PERSPECTIVE_PROJECTION) {
			GLU.gluPerspective(gl, 20f, (float) mViewportWidth / mViewportHeight, .1f, 100f);
		} else if (mMirrorApplied) {
			GLU.gluOrtho2D(gl, mViewRect.right, mViewRect.left,
					mViewRect.bottom, mViewRect.top);
		} else {
			GLU.gluOrtho2D(gl, mViewRect.left, mViewRect.right,
					mViewRect.bottom, mViewRect.top);
		}
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		gl.glViewport(0, 0, width, height);
		mViewportWidth = width;
		mViewportHeight = height;

		float ratio = (float) width / height;
		mViewRect.top = 1.0f;
		mViewRect.bottom = -1.0f;
		mViewRect.left = -ratio;
		mViewRect.right = ratio;
		updatePageRects();

		applyProjection(gl);
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		gl.glClearColor(0f, 0f, 0f, 1f);
		gl.glShadeModel(GL10.GL_SMOOTH);
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
		gl.glHint(GL10.GL_LINE_SMOOTH_HINT, GL10.GL_NICEST);
		gl.glHint(GL10.GL_POLYGON_SMOOTH_HINT, GL10.GL_NICEST);
		gl.glEnable(GL10.GL_LINE_SMOOTH);
		gl.glDisable(GL10.GL_DEPTH_TEST);
		gl.glDisable(GL10.GL_CULL_FACE);

		mObserver.onSurfaceCreated();
	}

	/**
	 * Removes CurlMesh from this renderer.
	 */
	public synchronized void removeCurlMesh(CurlMesh mesh) {
		while (mCurlMeshes.remove(mesh))
			;
	}

	/**
	 * Change background/clear color.
	 */
	public void setBackgroundColor(int color) {
		mBackgroundColor = color;
	}

	/**
	 * Set margins or padding. Note: margins are proportional. Meaning a value
	 * of .1f will produce a 10% margin.
	 */
	public synchronized void setMargins(float left, float top, float right,
			float bottom) {
		mMargins.left = left;
		mMargins.top = top;
		mMargins.right = right;
		mMargins.bottom = bottom;
		updatePageRects();
	}

	/**
	 * Sets visible page count to one or two. Should be either SHOW_ONE_PAGE or
	 * SHOW_TWO_PAGES.
	 */
	public synchronized void setViewMode(int viewmode) {
		if (viewmode == SHOW_ONE_PAGE) {
			mViewMode = viewmode;
			updatePageRects();
		} else if (viewmode == SHOW_TWO_PAGES) {
			mViewMode = viewmode;
			updatePageRects();
		}
	}

	/**
	 * Translates screen coordinates into view coordinates.
	 */
	public void translate(PointF pt) {
		pt.x = mViewRect.left + (mViewRect.width() * pt.x / mViewportWidth);
		pt.y = mViewRect.top - (-mViewRect.height() * pt.y / mViewportHeight);
	}

	/**
	 * Recalculates page rectangles.
	 */
	private void updatePageRects() {
		if (mViewRect.width() == 0 || mViewRect.height() == 0) {
			return;
		} else if (mViewMode == SHOW_ONE_PAGE) {
			mPageRectRight.set(mViewRect);
			mPageRectRight.left += mViewRect.width() * mMargins.left;
			mPageRectRight.right -= mViewRect.width() * mMargins.right;
			mPageRectRight.top += mViewRect.height() * mMargins.top;
			mPageRectRight.bottom -= mViewRect.height() * mMargins.bottom;

			mPageRectLeft.set(mPageRectRight);
			mPageRectLeft.offset(-mPageRectRight.width(), 0);

			int bitmapW = (int) ((mPageRectRight.width() * mViewportWidth) / mViewRect
					.width());
			int bitmapH = (int) ((mPageRectRight.height() * mViewportHeight) / mViewRect
					.height());
			mObserver.onPageSizeChanged(bitmapW, bitmapH);
		} else if (mViewMode == SHOW_TWO_PAGES) {
			mPageRectRight.set(mViewRect);
			mPageRectRight.left += mViewRect.width() * mMargins.left;
			mPageRectRight.right -= mViewRect.width() * mMargins.right;
			mPageRectRight.top += mViewRect.height() * mMargins.top;
			mPageRectRight.bottom -= mViewRect.height() * mMargins.bottom;

			mPageRectLeft.set(mPageRectRight);
			mPageRectLeft.right = (mPageRectLeft.right + mPageRectLeft.left) / 2;
			mPageRectRight.left = mPageRectLeft.right;

			int bitmapW = (int) ((mPageRectRight.width() * mViewportWidth) / mViewRect
					.width());
			int bitmapH = (int) ((mPageRectRight.height() * mViewportHeight) / mViewRect
					.height());
			mObserver.onPageSizeChanged(bitmapW, bitmapH);
		}
	}

	/**
	 * Observer for waiting render engine/state updates.
	 */
	public interface Observer {
		/**
		 * Called from onDrawFrame called before rendering is started. This is
		 * intended to be used for animation purposes.
		 */
		public void onDrawFrame();

		/**
		 * Called once page size is changed. Width and height tell the page size
		 * in pixels making it possible to update textures accordingly.
		 */
		public void onPageSizeChanged(int width, int height);

		/**
		 * Called from onSurfaceCreated to enable texture re-initialization etc
		 * what needs to be done when this happens.
		 */
		public void onSurfaceCreated();
	}
}
