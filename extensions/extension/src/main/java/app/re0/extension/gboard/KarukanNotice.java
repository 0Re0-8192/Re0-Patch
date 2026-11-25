package app.re0.extension.gboard;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * Short notices about karukan that the user would not see otherwise: while the models are missing (or karukan
 * stopped after an error) Gboard types as plain Mozc and nothing tells why.
 * <p>
 * Drawn on the keyboard itself, over its top edge, the way Gboard shows its own notices: a toast would not show,
 * since Gboard never asks for the notification permission and Android drops the toasts of an app without it.
 * A notice waits until the keyboard is up ({@link #flush()} runs with every command), and each one is shown once
 * per process, so a keyboard that works never shows any.
 */
final class KarukanNotice {
    static final String WAITING = "karukan：Wi-Fi で準備します";
    static final String FETCHING = "karukan：準備中";
    static final String READY = "karukan：準備できました";
    static final String FAILED = "karukan：取得に失敗（あとで再試行）";
    static final String STOPPED = "karukan：エラーで停止中";

    private static final long SHOWN_MS = 3500;

    private static final Set<String> done = new HashSet<>();
    private static volatile String pending;

    private KarukanNotice() {
    }

    /** Queues a notice; it is drawn at the next command while the keyboard is up. */
    static void show(String text) {
        synchronized (done) {
            if (done.contains(text)) {
                return;
            }
            pending = text;
        }
        flush();
    }

    /** Draws the queued notice if the keyboard is up. Cheap when nothing is queued. */
    static void flush() {
        String text = pending;
        if (text == null) {
            return;
        }
        InputMethodService ime = KarukanHook.service();
        if (ime == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Window window = ime.getWindow() != null ? ime.getWindow().getWindow() : null;
                View decor = window != null ? window.getDecorView() : null;
                if (!(decor instanceof FrameLayout) || !decor.isShown()) {
                    return;
                }
                synchronized (done) {
                    if (pending == null || !done.add(pending)) {
                        pending = null;
                        return;
                    }
                    pending = null;
                }
                FrameLayout frame = (FrameLayout) decor;
                TextView pill = pill(frame.getContext(), text);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                // Over the bottom row of keys, as Gboard's own notices: the window reaches under the navigation bar.
                int navigation = 0;
                android.view.WindowInsets insets = frame.getRootWindowInsets();
                if (insets != null) {
                    navigation = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                }
                params.bottomMargin = navigation + dp(frame.getContext(), 24);
                frame.addView(pill, params);
                pill.postDelayed(() -> frame.removeView(pill), SHOWN_MS);
            } catch (RuntimeException ignored) {
                // a notice is never worth an error
            }
        });
    }

    private static TextView pill(Context context, String text) {
        boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setTextColor(night ? 0xFF1B1B1F : 0xFFF4F5F7);
        view.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(night ? 0xFFE6E6E9 : 0xFF303034);
        background.setCornerRadius(dp(context, 24));
        view.setBackground(background);
        view.setElevation(dp(context, 6));
        return view;
    }

    private static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
