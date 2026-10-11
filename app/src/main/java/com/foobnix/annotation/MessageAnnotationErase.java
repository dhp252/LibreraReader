package com.foobnix.annotation;

/** The eraser tapped an annotation in the paged view. */
public class MessageAnnotationErase {
    /** 1-based, as Annotation.getPage(). */
    public final int page;
    public final int index;

    public MessageAnnotationErase(int page, int index) {
        this.page = page;
        this.index = index;
    }
}
