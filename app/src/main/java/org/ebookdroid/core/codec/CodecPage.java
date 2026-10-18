package org.ebookdroid.core.codec;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import com.foobnix.pdf.info.model.AnnotationType;

import org.ebookdroid.common.bitmaps.BitmapRef;
import org.ebookdroid.droids.mupdf.codec.TextWord;

import java.util.List;

public interface CodecPage {

	int getWidth();

	int getHeight();

	BitmapRef renderBitmap(int width, int height, RectF pageSliceBounds, boolean cache);

    BitmapRef renderBitmapSimple(int width, int height, RectF pageSliceBounds);


	Bitmap renderThumbnail(int width);

	Bitmap renderThumbnail(int width, int originW, int originH);

	List<PageLink> getPageLinks();

	List<Annotation> getAnnotations();
	List<Annotation> getAnnotationsImpl();

	public TextWord[][] getText();

	void recycle();

	boolean isRecycled();

    public void addAnnotation(float[] color, PointF[][] points, float width, float alpha);

	long getPageHandle();

	void addMarkupAnnotation(PointF[] quadPoints, AnnotationType type, float[] color);

	/** Fork (F-008): color and lines of each annotation, as {@link #getAnnotationsImpl} lists them; empty when unknown. */
	List<AnnotationLook> getAnnotationLooks();

	/** Fork (F-008): the comment of annotation {@code index} of the page; false when it could not be set. */
	boolean setAnnotationContents(int index, String text);

    String getPageHTML();

    String getPageHTMLWithImages();

    int getCharCount();

}
