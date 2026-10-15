package app.re0.extension.gboard;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * Puts a video or an image behind the keys of the keyboard.
 * <p>
 * The patch calls {@link #onAttached(View)} from {@code ShrinkableFrameView}, the view that has the background of the
 * theme (the tag ".keyboard-background"). It is behind the keys and fills the keyboard, so the media goes in it as
 * its first child. The part of the keyboard that has an opaque background, such as with a plain color theme, hides
 * the media. A theme with a photo, which has a see-through body, shows it.
 */
@SuppressWarnings("unused")
public final class BackgroundMedia {
    private static final String TAG = "GboardBackground";
    private static final String BACKGROUND_TAG = ".keyboard-background";

    private BackgroundMedia() {
    }

    /**
     * Injection point. Called when the view of the theme background is attached to the window.
     */
    public static void onAttached(View view) {
        try {
            if (!BACKGROUND_TAG.equals(view.getTag()) || !(view instanceof ViewGroup)) {
                return;
            }

            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i) instanceof BackgroundMediaView) {
                    return;
                }
            }

            group.addView(new BackgroundMediaView(view.getContext()), 0,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception ex) {
            Log.e(TAG, "onAttached failure", ex);
        }
    }
}
