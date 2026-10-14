package app.re0.extension.gboard;

import android.app.Application;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Overrides Gboard feature flags.
 * <p>
 * Gboard resolves every feature flag (local defaults, server side Phenotype values, OEM values...)
 * into a single final value. The patch hooks the getter of that final value, so every flag read
 * goes through {@link #getFlagValue(String, Object)} and an override here wins over all sources.
 * <p>
 * Overrides come from two places, the file winning over the patch option:
 * <ol>
 *     <li>The "Flag overrides" patch option, baked in at patch time.</li>
 *     <li>{@value #OVERRIDES_FILE_NAME} in the app's external files directory
 *     ({@code /sdcard/Android/data/<package>/files/}), read once per process start.</li>
 * </ol>
 * Both use the format {@code name=value}, one override per line or separated by {@code ;}.
 * Lines starting with {@code #} are ignored.
 */
@SuppressWarnings("unused")
public final class FlagOverridePatch {
    private static final String TAG = "GboardFlags";

    public static final String OVERRIDES_FILE_NAME = "flag_overrides.txt";

    /**
     * Injection point. The patch replaces the body with the "Flag overrides" option value.
     */
    private static String getPatchOverrides() {
        return "";
    }

    /**
     * Injection point. The patch replaces the body with the "Log flags" option value.
     */
    private static boolean isLogFlagsEnabled() {
        return false;
    }

    private static final Map<String, String> PATCH_OVERRIDES = parseOverrides(getPatchOverrides());

    private static final boolean LOG_FLAGS = isLogFlagsEnabled();

    /**
     * Patch option and file overrides combined.
     * Null until the app context is available and the overrides file has been read.
     */
    private static volatile Map<String, String> mergedOverrides;

    private static final Set<String> loggedFlags =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static Method currentApplicationMethod;

    private FlagOverridePatch() {
    }

    /**
     * Injection point. Called with the final value every time Gboard reads a flag.
     *
     * @param name  Flag name, for example {@code enable_extended_clipboard_history}.
     * @param value The value Gboard resolved for the flag.
     * @return The value to use instead.
     */
    public static Object getFlagValue(String name, Object value) {
        if (name == null || value == null) {
            return value;
        }

        try {
            String override = getOverrides().get(name);

            if (LOG_FLAGS && loggedFlags.add(name)) {
                Log.i(TAG, name + " = " + describe(value)
                        + (override == null ? "" : "  ->  " + override));
            }

            if (override == null) {
                return value;
            }

            Object overrideValue = parseValue(name, override, value);
            return overrideValue != null ? overrideValue : value;
        } catch (Exception ex) {
            Log.e(TAG, "getFlagValue failure: " + name, ex);
            return value;
        }
    }

    private static Map<String, String> getOverrides() {
        Map<String, String> overrides = mergedOverrides;
        if (overrides != null) {
            return overrides;
        }

        // Flags are read before the Application exists.
        // Until then only the patch option overrides are available.
        Application application = currentApplication();
        if (application == null) {
            return PATCH_OVERRIDES;
        }

        synchronized (FlagOverridePatch.class) {
            if (mergedOverrides == null) {
                Map<String, String> merged = new HashMap<>(PATCH_OVERRIDES);
                merged.putAll(readOverridesFile(application));
                mergedOverrides = merged;

                Log.i(TAG, "Active flag overrides: " + merged.size()
                        + " (patch option: " + PATCH_OVERRIDES.size() + ")");
            }
            return mergedOverrides;
        }
    }

    private static Map<String, String> readOverridesFile(Application application) {
        File directory = application.getExternalFilesDir(null);
        if (directory == null) {
            Log.w(TAG, "External files directory is not available");
            return Collections.emptyMap();
        }

        File file = new File(directory, OVERRIDES_FILE_NAME);
        if (!file.isFile()) {
            Log.i(TAG, "No overrides file at: " + file.getAbsolutePath());
            return Collections.emptyMap();
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        } catch (Exception ex) {
            Log.e(TAG, "Could not read: " + file.getAbsolutePath(), ex);
            return Collections.emptyMap();
        }

        Map<String, String> overrides = parseOverrides(content.toString());
        Log.i(TAG, "Loaded " + overrides.size() + " overrides from: " + file.getAbsolutePath());
        return overrides;
    }

    private static Map<String, String> parseOverrides(String text) {
        Map<String, String> overrides = new HashMap<>();
        if (text == null) {
            return overrides;
        }

        for (String entry : text.split("[;\\n]")) {
            entry = entry.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }

            int separator = entry.indexOf('=');
            if (separator <= 0) {
                Log.w(TAG, "Ignoring override without '=': " + entry);
                continue;
            }

            overrides.put(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
        }
        return overrides;
    }

    /**
     * @return The override converted to the type of the original value,
     * or null if it cannot be converted.
     */
    private static Object parseValue(String name, String override, Object original) {
        try {
            if (original instanceof Boolean) {
                if (override.equalsIgnoreCase("true") || override.equals("1")) {
                    return Boolean.TRUE;
                }
                if (override.equalsIgnoreCase("false") || override.equals("0")) {
                    return Boolean.FALSE;
                }
            } else if (original instanceof Long) {
                return Long.parseLong(override);
            } else if (original instanceof Double) {
                return Double.parseDouble(override);
            } else if (original instanceof String) {
                return override;
            }
        } catch (NumberFormatException ignored) {
            // Logged below.
        }

        if (loggedFlags.add("invalid:" + name)) {
            Log.w(TAG, "Ignoring override " + name + "=" + override + " for flag of type "
                    + original.getClass().getSimpleName());
        }
        return null;
    }

    private static String describe(Object value) {
        if (value instanceof byte[]) {
            return "<" + ((byte[]) value).length + " bytes>";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return String.valueOf(value);
    }

    private static Application currentApplication() {
        try {
            if (currentApplicationMethod == null) {
                currentApplicationMethod = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication");
            }
            return (Application) currentApplicationMethod.invoke(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
