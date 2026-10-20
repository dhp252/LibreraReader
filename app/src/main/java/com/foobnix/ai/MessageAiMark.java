package com.foobnix.ai;

/** An AI mark was tapped in book mode: the reader screen shows it (AiMarkDialog). */
public class MessageAiMark {
    public final AiHighlights.Band band;

    public MessageAiMark(AiHighlights.Band band) {
        this.band = band;
    }
}
