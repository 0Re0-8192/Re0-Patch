package app.re0.extension.gboard;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;

/**
 * The application context, for code that runs without a view or an activity.
 */
final class AppContext {
    private static Method currentApplicationMethod;

    private AppContext() {
    }

    static Context get() {
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
