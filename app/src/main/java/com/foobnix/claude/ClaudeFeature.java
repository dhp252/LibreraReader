package com.foobnix.claude;

/**
 * The features an outside Claude can answer ahead through the WebDAV folder: each reads a chapter
 * (or the whole book) and gives one answer with nothing asked of the reader at that moment.
 * <p>
 * {@link #format} names the reply format of the feature's prompt. Raise it whenever the prompt's
 * reply format changes: answers written for another format are rejected, not half read.
 * {@code ClaudeFeatureTest} fails when a prompt changes and its recorded hash does not.
 */
public enum ClaudeFeature {
    /** Key passages: ideas, their quotes and must-read paragraphs (AiStore; X-ray marks and Recall use them). */
    IDEAS("ideas", 1),
    /** A chapter's summary, or the whole book's ({@link ClaudeAnswers#BOOK}). */
    SUMMARY("summary", 1),
    /** Skeleton first: the whole book's parts and chapters in a line each ({@link ClaudeAnswers#BOOK}). */
    SKELETON("skeleton", 1),
    PRE_QUESTIONS("prequestions", 1),
    /** X-ray: how well each idea of the chapter is supported. */
    XRAY("xray", 1),
    /** Lens: the concept bridge of a chapter. */
    LENS("lens", 1),
    ZOOM("zoom", 1),
    UPDATED("updated", 1),
    PICTURES("pictures", 1);

    /** Its name in answer files and in the skill. */
    public final String id;
    public final int format;

    ClaudeFeature(String id, int format) {
        this.id = id;
        this.format = format;
    }

    public static ClaudeFeature of(String id) {
        for (ClaudeFeature f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
