package com.foobnix.tts;

/** A word was double-tapped while reading aloud: continue reading from its sentence. */
public class MessageTtsReadFrom {
    /** Document page of the word. */
    public final int page;
    /** The word and a few after it, see {@link TTSHighlight#anchorFrom}. */
    public final String anchor;
    /** Where the word sits among the words of the page, 0..1. */
    public final float position;

    public MessageTtsReadFrom(int page, String anchor, float position) {
        this.page = page;
        this.anchor = anchor;
        this.position = position;
    }
}
