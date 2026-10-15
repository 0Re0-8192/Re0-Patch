package app.re0.extension.gboard;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Adds the entry of the video and image background to the top of the theme screen of Gboard.
 * <p>
 * The patch calls {@link #decorate(View)} with the view that {@code ThemeListingFragment} creates: a FrameLayout that
 * has the grid of the themes. The entry is a row above the grid, and opens {@link BackgroundPickerActivity}.
 */
@SuppressWarnings("unused")
public final class ThemeScreenHook {
    private static final String TAG = "GboardThemeScreen";
    private static final int ROW_HEIGHT_DP = 56;

    private ThemeScreenHook() {
    }

    /**
     * Injection point. Called with the view of the theme screen, right before it is returned. It can be null.
     */
    public static void decorate(View root) {
        try {
            if (!(root instanceof FrameLayout)) {
                return;
            }

            final FrameLayout frame = (FrameLayout) root;
            final Context context = frame.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            int height = Math.round(ROW_HEIGHT_DP * density);

            // The grid of the themes goes under the row.
            View grid = null;
            for (int i = 0; i < frame.getChildCount(); i++) {
                View child = frame.getChildAt(i);
                if (child.getClass().getName().contains("RecyclerView")) {
                    grid = child;
                    break;
                }
            }
            if (grid == null) {
                return;
            }
            ViewGroup.MarginLayoutParams gridParams = (ViewGroup.MarginLayoutParams) grid.getLayoutParams();
            gridParams.topMargin += height;
            grid.setLayoutParams(gridParams);

            TextView row = new TextView(context);
            row.setText("動画・画像の背景を設定する（Re0）  ›");
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(24 * density), 0, Math.round(24 * density), 0);
            TypedValue color = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true)) {
                row.setTextColor(context.getColor(color.resourceId));
            }
            TypedValue ripple = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                row.setBackgroundResource(ripple.resourceId);
            }
            row.setClickable(true);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(context, BackgroundPickerActivity.class);
                context.startActivity(intent);
            });
            frame.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP));
        } catch (Exception ex) {
            Log.e(TAG, "decorate failure", ex);
        }
    }
}
