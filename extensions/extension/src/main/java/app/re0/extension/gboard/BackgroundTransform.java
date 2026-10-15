package app.re0.extension.gboard;

/**
 * Where a picture goes in a view so that it covers the view, shows the part around a given center, and is
 * zoomed. Used by the keyboard and by the preview of the picker, so both show the same part.
 */
final class BackgroundTransform {
    /** The position of the picture in the view, and the scale of the picture. */
    float left;
    float top;
    float scale;

    /** The center, kept inside the picture, so the view stays covered. */
    float centerX;
    float centerY;

    /**
     * @param mediaWidth  The width of the picture.
     * @param centerX     The part of the width of the picture (0 to 1) that is at the middle of the view.
     * @param zoom        1 shows as much as possible of the picture, larger zooms in.
     */
    void compute(float mediaWidth, float mediaHeight, float viewWidth, float viewHeight,
                 float centerX, float centerY, float zoom) {
        scale = Math.max(viewWidth / mediaWidth, viewHeight / mediaHeight) * Math.max(1f, zoom);

        float width = mediaWidth * scale;
        float height = mediaHeight * scale;
        left = clamp(viewWidth / 2f - centerX * width, viewWidth - width, 0f);
        top = clamp(viewHeight / 2f - centerY * height, viewHeight - height, 0f);

        this.centerX = (viewWidth / 2f - left) / width;
        this.centerY = (viewHeight / 2f - top) / height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
