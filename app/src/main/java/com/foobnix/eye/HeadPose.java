package com.foobnix.eye;

/**
 * Head pose from MediaPipe's facial transformation matrix: 4×4, column-major (MatrixData's
 * default layout), mapping the canonical face to the seen one. The rotation part is read as
 * roll · yaw · pitch (Z · Y · X): yaw turns the head left / right, pitch nods it.
 */
public final class HeadPose {

    private HeadPose() {
    }

    /** @return {yaw, pitch, roll} in degrees; zeros for a matrix that is not 4×4 */
    public static float[] angles(float[] m) {
        if (m == null || m.length < 16) {
            return new float[3];
        }
        // r[row][col] = m[col * 4 + row], each column divided by its length (the matrix may scale)
        final double[][] r = new double[3][3];
        for (int col = 0; col < 3; col++) {
            final double n = Math.sqrt(m[col * 4] * m[col * 4] + m[col * 4 + 1] * m[col * 4 + 1]
                    + m[col * 4 + 2] * m[col * 4 + 2]);
            for (int row = 0; row < 3; row++) {
                r[row][col] = n > 0 ? m[col * 4 + row] / n : 0;
            }
        }
        final double yaw = Math.asin(Math.max(-1, Math.min(1, -r[2][0])));
        final double pitch = Math.atan2(r[2][1], r[2][2]);
        final double roll = Math.atan2(r[1][0], r[0][0]);
        return new float[]{(float) Math.toDegrees(yaw), fold((float) Math.toDegrees(pitch)), (float) Math.toDegrees(roll)};
    }

    /**
     * A face the camera can see never nods past 90°: a pitch near ±180° means the matrix's depth
     * axis points the other way from what we assumed, not a face upside down. Folded back so a
     * reader looking straight at the screen reads about 0 either way.
     */
    static float fold(float pitch) {
        if (pitch > 90) {
            return pitch - 180;
        }
        if (pitch < -90) {
            return pitch + 180;
        }
        return pitch;
    }
}
