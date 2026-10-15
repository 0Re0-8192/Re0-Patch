package app.re0.extension.gboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Reads the switches that the patches add to the Gboard settings.
 * <p>
 * Gboard keeps its settings in the default preferences of the device protected storage, so they can be read
 * before the device is unlocked. The settings screen writes the switches there, and not in the default
 * preferences of the credential protected storage, which is what {@code getDefaultSharedPreferences(context)}
 * gives. So the device protected storage is read first, and the other one is used as a fallback.
 */
final class SettingsReader {
    private static final String TAG = "GboardSettings";

    private SettingsReader() {
    }

    static boolean getBoolean(Context context, String key, boolean defaultValue) {
        try {
            Context application = context.getApplicationContext();
            if (application == null) {
                application = context;
            }

            Context deviceProtected = application.isDeviceProtectedStorage()
                    ? application : application.createDeviceProtectedStorageContext();
            if (deviceProtected != null) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(deviceProtected);
                if (preferences.contains(key)) {
                    return preferences.getBoolean(key, defaultValue);
                }
            }

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(application);
            return preferences.getBoolean(key, defaultValue);
        } catch (Exception ex) {
            Log.w(TAG, "Could not read the setting " + key + ", using the default");
            return defaultValue;
        }
    }
}
