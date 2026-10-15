package app.re0.extension.gboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * The settings of the custom keyboard background: a video or an image, and for each of the two screens of a
 * foldable phone, the part of it that is shown.
 * <p>
 * The picker activity writes them, and the keyboard reads them. The media file is copied into the device
 * protected storage, which the keyboard can read before the device is unlocked.
 */
final class BackgroundSettings {
    static final int TYPE_NONE = 0;
    static final int TYPE_IMAGE = 1;
    static final int TYPE_VIDEO = 2;

    /** The cover screen of a foldable phone, or any phone. */
    static final int MODE_SMALL = 0;
    /** The inner screen of a foldable phone, or a tablet. */
    static final int MODE_LARGE = 1;

    static final String KEY_ENABLED = "re0_bgm_enabled";
    static final String KEY_TYPE = "re0_bgm_type";
    static final String KEY_VERSION = "re0_bgm_version";
    static final String KEY_NAME = "re0_bgm_name";

    /** The screen is large from this width on, as the keyboard layouts of Gboard do. */
    private static final int LARGE_WIDTH_DP = 600;

    private static final float DEFAULT_ASPECT_SMALL = 1.3f;
    private static final float DEFAULT_ASPECT_LARGE = 2.4f;

    static final float MAX_ZOOM = 6f;

    private BackgroundSettings() {
    }

    static int modeOf(Context context) {
        return context.getResources().getConfiguration().screenWidthDp >= LARGE_WIDTH_DP ? MODE_LARGE : MODE_SMALL;
    }

    static File mediaFile(Context context) {
        Context application = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Context deviceProtected = application.isDeviceProtectedStorage()
                ? application : application.createDeviceProtectedStorageContext();
        return new File((deviceProtected != null ? deviceProtected : application).getFilesDir(), "re0_background.bin");
    }

    static boolean isEnabled(Context context) {
        return SettingsReader.getBoolean(context, KEY_ENABLED, false);
    }

    static int type(Context context) {
        return SettingsReader.preferences(context).getInt(KEY_TYPE, TYPE_NONE);
    }

    static long version(Context context) {
        return SettingsReader.preferences(context).getLong(KEY_VERSION, 0);
    }

    static float centerX(Context context, int mode) {
        return SettingsReader.preferences(context).getFloat("re0_bgm_cx_" + mode, 0.5f);
    }

    static float centerY(Context context, int mode) {
        return SettingsReader.preferences(context).getFloat("re0_bgm_cy_" + mode, 0.5f);
    }

    static float zoom(Context context, int mode) {
        return SettingsReader.preferences(context).getFloat("re0_bgm_zoom_" + mode, 1f);
    }

    /** Width divided by height of the keyboard background on the screen, as it was last seen. */
    static float aspect(Context context, int mode) {
        return SettingsReader.preferences(context).getFloat(
                "re0_bgm_aspect_" + mode, mode == MODE_LARGE ? DEFAULT_ASPECT_LARGE : DEFAULT_ASPECT_SMALL);
    }

    static void saveView(Context context, int mode, float centerX, float centerY, float zoom) {
        SettingsReader.preferences(context).edit()
                .putFloat("re0_bgm_cx_" + mode, centerX)
                .putFloat("re0_bgm_cy_" + mode, centerY)
                .putFloat("re0_bgm_zoom_" + mode, zoom)
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply();
    }

    static void saveAspect(Context context, int mode, float aspect) {
        SettingsReader.preferences(context).edit().putFloat("re0_bgm_aspect_" + mode, aspect).apply();
    }

    static void saveMedia(Context context, int type, String name) {
        SharedPreferences.Editor editor = SettingsReader.preferences(context).edit()
                .putInt(KEY_TYPE, type)
                .putString(KEY_NAME, name)
                .putBoolean(KEY_ENABLED, type != TYPE_NONE)
                .putLong(KEY_VERSION, System.currentTimeMillis());
        for (int mode = MODE_SMALL; mode <= MODE_LARGE; mode++) {
            editor.putFloat("re0_bgm_cx_" + mode, 0.5f)
                    .putFloat("re0_bgm_cy_" + mode, 0.5f)
                    .putFloat("re0_bgm_zoom_" + mode, 1f);
        }
        editor.apply();
    }

    static void saveEnabled(Context context, boolean enabled) {
        SettingsReader.preferences(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply();
    }

    static String name(Context context) {
        return SettingsReader.preferences(context).getString(KEY_NAME, "");
    }
}
