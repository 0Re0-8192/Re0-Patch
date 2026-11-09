package app.re0.extension.gboard;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

/**
 * Lets the theme builder of Gboard take a video or a GIF.
 * <p>
 * The picker of the builder also offers videos now. Gboard loads the first frame of a video or a GIF as the image
 * of the theme, so the crop and brightness pages of the builder work as they do for a photo. This class
 * keeps the picked file, and when the theme is saved, makes it the video background, with the part that the
 * user cropped.
 */
@SuppressWarnings("unused")
public final class ThemeBuilderHook {
    private static final String TAG = "GboardThemeBuilder";

    private static Thread copyThread;
    private static File pendingFile;
    // Counts the picks. A copy that is no longer the latest pick must not become the pending file.
    private static int pickCount;
    private static int pendingType = BackgroundSettings.TYPE_NONE;
    private static String pendingName = "";
    private static int pendingMode = BackgroundSettings.MODE_SMALL;

    private ThemeBuilderHook() {
    }

    /**
     * Injection point. Called with the activity of the builder and the image that the user picked.
     */
    public static void onPicked(final Activity activity, final Uri uri) {
        try {
            String mime = activity.getContentResolver().getType(uri);
            int type = BackgroundSettings.TYPE_NONE;
            if (mime != null && mime.startsWith("video/")) {
                type = BackgroundSettings.TYPE_VIDEO;
            } else if ("image/gif".equals(mime)) {
                type = BackgroundSettings.TYPE_IMAGE;
            }

            BackgroundSettings.log(activity, "builder: picked " + mime + " -> type " + type);
            final int pick;
            File previous;
            synchronized (ThemeBuilderHook.class) {
                previous = pendingFile;
                pick = ++pickCount;
                pendingType = type;
                pendingMode = BackgroundSettings.modeOf(activity);
                pendingName = displayName(activity, uri);
                pendingFile = null;
                copyThread = null;
            }
            if (previous != null) {
                previous.delete();
            }
            if (type == BackgroundSettings.TYPE_NONE) {
                return;
            }

            // A file of its own for each pick, so that two copies never write to the same file.
            final File file = new File(BackgroundSettings.mediaFile(activity, BackgroundSettings.modeOf(activity)).getPath()
                    + ".pending" + pick);
            Thread thread = new Thread(() -> {
                try (InputStream in = activity.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    synchronized (ThemeBuilderHook.class) {
                        if (pick == pickCount) {
                            pendingFile = file;
                            return;
                        }
                    }
                    file.delete();
                } catch (Exception ex) {
                    Log.e(TAG, "Could not copy the picked file", ex);
                    file.delete();
                }
            }, "GboardThemeBuilderCopy");
            synchronized (ThemeBuilderHook.class) {
                copyThread = thread;
            }
            thread.start();
        } catch (Exception ex) {
            Log.e(TAG, "onPicked failure", ex);
        }
    }

    /**
     * Injection point. Called with the builder of the theme ({@code UserImageThemeBuilder}), when the theme is
     * saved.
     */
    public static void onSave(Object builder) {
        try {
            Context context = AppContext.get();
            if (context == null) {
                return;
            }

            Thread thread;
            int type;
            String name;
            int slot;
            synchronized (ThemeBuilderHook.class) {
                thread = copyThread;
                type = pendingType;
                name = pendingName;
                slot = pendingMode;
            }

            if (type == BackgroundSettings.TYPE_NONE) {
                // A theme made from a photo does not have a video behind it.
                BackgroundSettings.saveEnabled(context, false);
                BackgroundSettings.log(context, "builder: saved a photo theme, background off");
                return;
            }

            if (thread != null) {
                thread.join(60_000);
            }
            File pending;
            synchronized (ThemeBuilderHook.class) {
                pending = pendingFile;
            }
            if (pending == null || !pending.isFile()) {
                BackgroundSettings.log(context, "builder: saved, but no copied file (" + pending + ")");
                return;
            }

            File active = BackgroundSettings.mediaFile(context, slot);
            // renameTo replaces the old file in one step, so the old background stays if the move fails.
            if (!pending.renameTo(active)) {
                Log.e(TAG, "Could not move the picked file");
                return;
            }
            BackgroundSettings.saveMedia(context, slot, type, name);
            BackgroundSettings.log(context, "builder: saved for screen " + slot + ", background on, type " + type
                    + ", " + name);

            // The part of the picture that the user framed, as parts of its size.
            Rect crop = (Rect) field(builder, "c").get(builder);
            int width = field(builder, "l").getInt(builder);
            int height = field(builder, "m").getInt(builder);
            if (crop == null || width <= 0 || height <= 0 || crop.width() <= 0 || crop.height() <= 0) {
                return;
            }

            float centerX = (crop.left + crop.right) / 2f / width;
            float centerY = (crop.top + crop.bottom) / 2f / height;
            float partWidth = crop.width() / (float) width;
            float partHeight = crop.height() / (float) height;

            // The screen where the theme was made gets the part. The other screen, if it has no file of its own,
            // shows this file, so it gets the same part, and can be changed in the picker.
            for (int mode = BackgroundSettings.MODE_SMALL; mode <= BackgroundSettings.MODE_LARGE; mode++) {
                if (mode != slot && BackgroundSettings.hasMedia(context, mode)) {
                    continue;
                }
                float zoom = zoomFor(width / (float) height, BackgroundSettings.aspect(context, mode),
                        partWidth, partHeight);
                BackgroundSettings.saveView(context, mode, centerX, centerY, zoom);
                BackgroundSettings.log(context, "builder: screen " + mode + " center " + centerX + "," + centerY
                        + " zoom " + zoom);
            }
        } catch (Exception ex) {
            Log.e(TAG, "onSave failure", ex);
            Context context = AppContext.get();
            if (context != null) {
                BackgroundSettings.log(context, "builder: save failed: " + ex);
            }
        }
    }

    /**
     * The zoom with which a view of the given shape shows at most the part of the picture.
     * The view covers the picture at zoom 1.
     */
    private static float zoomFor(float mediaAspect, float viewAspect, float partWidth, float partHeight) {
        // In units where the picture is mediaAspect wide and 1 high, and the view viewAspect wide and 1 high.
        float cover = Math.max(viewAspect / mediaAspect, 1f);
        float zoomForHeight = 1f / (cover * partHeight);
        float zoomForWidth = viewAspect / (cover * mediaAspect * partWidth);
        float zoom = Math.max(zoomForHeight, zoomForWidth);
        return Math.max(1f, Math.min(BackgroundSettings.MAX_ZOOM, zoom));
    }

    private static Field field(Object object, String name) throws NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
