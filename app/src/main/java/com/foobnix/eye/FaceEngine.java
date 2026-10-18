package com.foobnix.eye;

import android.graphics.Bitmap;

/** Finds the reader's face in a camera frame. Called on the camera thread only. */
interface FaceEngine {

    /**
     * @param upright the frame turned upright (ARGB_8888), not kept after the call
     * @param t       the frame's time, ms, increasing from call to call
     * @return the frame's face numbers; luma and rotation are filled by the caller
     */
    FaceFrame detect(Bitmap upright, long t);

    void close();
}
