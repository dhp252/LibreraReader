package com.foobnix.annotation;

/** A tap that landed on a separate mark: the mark and the 0-based document page it was tapped on. */
public final class MarkTap {

    private MarkTap() {
    }

    public static final class Hit {
        public final Mark mark;
        public final int page;

        public Hit(Mark mark, int page) {
            this.mark = mark;
            this.page = page;
        }
    }
}
