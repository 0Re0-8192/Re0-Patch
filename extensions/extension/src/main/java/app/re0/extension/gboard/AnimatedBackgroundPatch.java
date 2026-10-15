package app.re0.extension.gboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import java.util.WeakHashMap;

/**
 * Moves the background of the keyboard slowly.
 * <p>
 * {@code SoftKeyboardView}, the view of the keys, calls {@link #onAttached(View)} when it is attached, and
 * {@link #draw(View, Canvas)} after its background is drawn and before the keys are. Two effects are drawn there:
 * <ul>
 * <li>Pan: the background image, if there is one, is drawn again, a little larger and moving in a slow loop.</li>
 * <li>Color: a color that slowly changes its hue is drawn over the background.</li>
 * </ul>
 * Both are switches in the Gboard settings. The view is redrawn about 25 times a second, only while it is shown.
 */
@SuppressWarnings("unused")
public final class AnimatedBackgroundPatch {
    private static final String TAG = "GboardAnimatedBg";

    public static final String COLOR_PREFERENCE_KEY = "re0_bg_color_cycle";
    public static final String PAN_PREFERENCE_KEY = "re0_bg_pan";

    private static final long FRAME_MILLIS = 40;
    private static final long IDLE_MILLIS = 500;
    private static final long SETTINGS_MILLIS = 1000;

    /** A full turn of the hue takes this long. */
    private static final float COLOR_PERIOD_MILLIS = 60_000f;
    private static final int COLOR_ALPHA = 120;
    private static final float COLOR_SATURATION = 0.75f;
    private static final float COLOR_VALUE = 0.65f;

    /** The image is drawn this much larger, and moves up to this part of the size to each side. */
    private static final float PAN_SCALE = 1.14f;
    private static final float PAN_RANGE = 0.06f;
    private static final float PAN_PERIOD_X_MILLIS = 37_000f;
    private static final float PAN_PERIOD_Y_MILLIS = 53_000f;

    private static final float[] hsv = new float[3];
    private static final int[] location = new int[2];
    private static final WeakHashMap<View, Ticker> TICKERS = new WeakHashMap<>();

    private static boolean colorEnabled = true;
    private static boolean panEnabled = true;
    private static long settingsReadAt = Long.MIN_VALUE;

    private AnimatedBackgroundPatch() {
    }

    /**
     * Injection point. Called when the keyboard view is attached to the window.
     */
    public static void onAttached(View view) {
        try {
            if (TICKERS.containsKey(view)) {
                return;
            }
            // A view without a background is not drawn by default.
            view.setWillNotDraw(false);

            Ticker ticker = new Ticker(view);
            TICKERS.put(view, ticker);
            view.postOnAnimation(ticker);
        } catch (Exception ex) {
            Log.e(TAG, "onAttached failure", ex);
        }
    }

    /**
     * Injection point. Called from onDraw of the keyboard view, after its background is drawn.
     */
    public static void draw(View view, Canvas canvas) {
        try {
            readSettings(view.getContext());
            if (!panEnabled && !colorEnabled) {
                return;
            }

            long now = SystemClock.uptimeMillis();
            if (panEnabled) {
                drawPan(view, canvas, now);
            }
            if (colorEnabled) {
                hsv[0] = (now % (long) COLOR_PERIOD_MILLIS) / COLOR_PERIOD_MILLIS * 360f;
                hsv[1] = COLOR_SATURATION;
                hsv[2] = COLOR_VALUE;
                canvas.drawColor(Color.HSVToColor(COLOR_ALPHA, hsv));
            }
        } catch (Exception ex) {
            Log.e(TAG, "draw failure", ex);
        }
    }

    /**
     * Draws the background image again, moved and enlarged. The image is the background of the view, or, if the view
     * has none, of the nearest parent that has an image.
     */
    private static void drawPan(View view, Canvas canvas, long now) {
        View owner = view;
        Drawable background = image(owner);
        while (background == null) {
            ViewParent parent = owner.getParent();
            if (!(parent instanceof View)) {
                return;
            }
            owner = (View) parent;
            background = image(owner);
        }

        int width = owner.getWidth();
        int height = owner.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float dx = (float) Math.sin(now / PAN_PERIOD_X_MILLIS * 2 * Math.PI) * width * PAN_RANGE;
        float dy = (float) Math.cos(now / PAN_PERIOD_Y_MILLIS * 2 * Math.PI) * height * PAN_RANGE;

        canvas.save();
        if (owner != view) {
            // The canvas has the origin at the view, the image at the owner.
            view.getLocationInWindow(location);
            int viewX = location[0];
            int viewY = location[1];
            owner.getLocationInWindow(location);
            canvas.translate(location[0] - viewX, location[1] - viewY);
        }
        canvas.translate(width / 2f + dx, height / 2f + dy);
        canvas.scale(PAN_SCALE, PAN_SCALE);
        canvas.translate(-width / 2f, -height / 2f);
        background.draw(canvas);
        canvas.restore();
    }

    /**
     * @return The background of the view, if it is more than a color.
     */
    private static Drawable image(View view) {
        Drawable background = view.getBackground();
        return background == null || background instanceof ColorDrawable ? null : background;
    }

    private static void readSettings(Context context) {
        long now = SystemClock.uptimeMillis();
        if (now - settingsReadAt < SETTINGS_MILLIS && settingsReadAt != Long.MIN_VALUE) {
            return;
        }
        settingsReadAt = now;

        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            colorEnabled = preferences.getBoolean(COLOR_PREFERENCE_KEY, true);
            panEnabled = preferences.getBoolean(PAN_PREFERENCE_KEY, true);
        } catch (Exception ex) {
            Log.w(TAG, "Could not read the settings, using the defaults");
        }
    }

    /**
     * Redraws the view in a loop, while it is attached.
     */
    private static final class Ticker implements Runnable {
        private final View view;

        Ticker(View view) {
            this.view = view;
        }

        @Override
        public void run() {
            try {
                if (!view.isAttachedToWindow()) {
                    TICKERS.remove(view);
                    return;
                }

                readSettings(view.getContext());
                boolean animated = colorEnabled || panEnabled;
                if (animated && view.isShown() && view.getWidth() > 0) {
                    view.invalidate();
                    view.postOnAnimationDelayed(this, FRAME_MILLIS);
                } else {
                    view.postOnAnimationDelayed(this, IDLE_MILLIS);
                }
            } catch (Exception ex) {
                Log.e(TAG, "ticker failure", ex);
                TICKERS.remove(view);
            }
        }
    }
}
