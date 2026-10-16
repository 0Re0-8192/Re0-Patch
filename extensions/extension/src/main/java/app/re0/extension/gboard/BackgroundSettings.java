package app.re0.extension.gboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The settings of the custom keyboard background: a video, a GIF or an image for each of the two screens of a
 * foldable phone, and for each the part of it that is shown.
 * <p>
 * The picker activity and the theme builder write them, and the keyboard reads them. The media files are copied
 * into the device protected storage, which the keyboard can read before the device is unlocked. A screen
 * without a file of its own shows the file of the other screen.
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
    static final String KEY_VERSION = "re0_bgm_version";

    /** The file and the keys of the single background, from before each screen had its own. */
    private static final String LEGACY_FILE = "re0_background.bin";
    private static final String LEGACY_KEY_TYPE = "re0_bgm_type";
    private static final String LEGACY_KEY_NAME = "re0_bgm_name";

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

    private static File directory(Context context) {
        Context application = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Context deviceProtected = application.isDeviceProtectedStorage()
                ? application : application.createDeviceProtectedStorageContext();
        return (deviceProtected != null ? deviceProtected : application).getFilesDir();
    }

    /** The media file of a screen. */
    static File mediaFile(Context context, int slot) {
        return new File(directory(context), "re0_background_" + slot + ".bin");
    }

    static boolean isEnabled(Context context) {
        return SettingsReader.getBoolean(context, KEY_ENABLED, false);
    }

    static int slotType(Context context, int slot) {
        return SettingsReader.preferences(context).getInt("re0_bgm_type_" + slot, TYPE_NONE);
    }

    static String slotName(Context context, int slot) {
        return SettingsReader.preferences(context).getString("re0_bgm_name_" + slot, "");
    }

    /** Whether the screen has a media file of its own. */
    static boolean hasMedia(Context context, int slot) {
        return slotType(context, slot) != TYPE_NONE && mediaFile(context, slot).isFile();
    }

    /**
     * @return The screen whose media file the given screen shows: its own, or else the one of the other screen.
     *         -1 if there is none.
     */
    static int resolveSlot(Context context, int mode) {
        if (hasMedia(context, mode)) {
            return mode;
        }
        int other = mode == MODE_SMALL ? MODE_LARGE : MODE_SMALL;
        return hasMedia(context, other) ? other : -1;
    }

    static boolean hasAnyMedia(Context context) {
        return hasMedia(context, MODE_SMALL) || hasMedia(context, MODE_LARGE);
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

    /**
     * Sets the media file of a screen, which is already in {@link #mediaFile(Context, int)}. The part that is shown
     * goes back to the middle.
     */
    static void saveMedia(Context context, int slot, int type, String name) {
        SettingsReader.preferences(context).edit()
                .putInt("re0_bgm_type_" + slot, type)
                .putString("re0_bgm_name_" + slot, name)
                .putFloat("re0_bgm_cx_" + slot, 0.5f)
                .putFloat("re0_bgm_cy_" + slot, 0.5f)
                .putFloat("re0_bgm_zoom_" + slot, 1f)
                .putBoolean(KEY_ENABLED, true)
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply();
    }

    static void deleteMedia(Context context, int slot) {
        mediaFile(context, slot).delete();
        SettingsReader.preferences(context).edit()
                .putInt("re0_bgm_type_" + slot, TYPE_NONE)
                .putString("re0_bgm_name_" + slot, "")
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply();
    }

    static void saveEnabled(Context context, boolean enabled) {
        SettingsReader.preferences(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply();
    }

    /**
     * Before each screen had its own media file, there was one. It becomes the file of both screens.
     */
    static synchronized void migrate(Context context) {
        try {
            File legacy = new File(directory(context), LEGACY_FILE);
            if (!legacy.isFile()) {
                return;
            }

            SharedPreferences preferences = SettingsReader.preferences(context);
            int type = preferences.getInt(LEGACY_KEY_TYPE, TYPE_NONE);
            String name = preferences.getString(LEGACY_KEY_NAME, "");
            if (type != TYPE_NONE && !hasAnyMedia(context)) {
                File small = mediaFile(context, MODE_SMALL);
                File large = mediaFile(context, MODE_LARGE);
                try (InputStream in = new FileInputStream(legacy);
                     OutputStream out = new FileOutputStream(small)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                try (InputStream in = new FileInputStream(legacy);
                     OutputStream out = new FileOutputStream(large)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                preferences.edit()
                        .putInt("re0_bgm_type_" + MODE_SMALL, type)
                        .putString("re0_bgm_name_" + MODE_SMALL, name)
                        .putInt("re0_bgm_type_" + MODE_LARGE, type)
                        .putString("re0_bgm_name_" + MODE_LARGE, name)
                        .putLong(KEY_VERSION, System.currentTimeMillis())
                        .apply();
            }
            legacy.delete();
            preferences.edit().remove(LEGACY_KEY_TYPE).remove(LEGACY_KEY_NAME).apply();
        } catch (Exception ex) {
            log(context, "migrate failed: " + ex);
        }
    }

    /** What the background did and why, for the settings screen. The newest lines last, about 12 of them. */
    static synchronized void log(Context context, String line) {
        try {
            SharedPreferences preferences = SettingsReader.preferences(context);
            String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            String[] old = preferences.getString("re0_bgm_log", "").split("\n");
            StringBuilder builder = new StringBuilder();
            int start = Math.max(0, old.length - 11);
            for (int i = start; i < old.length; i++) {
                if (!old[i].isEmpty()) {
                    builder.append(old[i]).append('\n');
                }
            }
            builder.append(time).append(' ').append(line);
            preferences.edit().putString("re0_bgm_log", builder.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static String logText(Context context) {
        return SettingsReader.preferences(context).getString("re0_bgm_log", "");
    }
}
