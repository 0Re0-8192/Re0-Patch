package app.re0.extension.gboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Chooses the video or the image of the keyboard background, and the part of it that is shown on each screen.
 * Opened from the Gboard settings.
 */
public final class BackgroundPickerActivity extends Activity {
    /** The request code of the picker of the cover screen. The inner screen is one more. */
    private static final int REQUEST_PICK = 10;
    private static final int PREVIEW_SIDE = 1600;

    private static final int MAX_CONTENT_DP = 640;

    private TextView[] status = new TextView[2];
    private Button[] deleteButtons = new Button[2];
    private Switch enabledSwitch;
    private Preview[] previews;
    private boolean updating;
    private LinearLayout content;
    private Palette palette;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The screen has its own title, in the style of the Gboard settings.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        palette = new Palette(this);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(32));

        // The top bar: a back arrow and the title.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(palette.text);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        TextView title = new TextView(this);
        title.setText("背景の動画・画像");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTextColor(palette.text);
        title.setPadding(dp(8), dp(8), dp(8), dp(20));
        content.addView(title);

        // Card: the switch, and what it needs.
        LinearLayout switchCard = card();
        enabledSwitch = new Switch(this);
        enabledSwitch.setText("背景に表示する");
        enabledSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        enabledSwitch.setTextColor(palette.text);
        enabledSwitch.setSwitchPadding(dp(8));
        enabledSwitch.setOnCheckedChangeListener((CompoundButton button, boolean checked) -> {
            if (!updating) {
                BackgroundSettings.saveEnabled(this, checked);
            }
        });
        switchCard.addView(enabledSwitch);

        TextView note = label("閉じた画面と開いた画面で、別の動画・画像を流せます。選んでいない画面には、もう一方の動画・画像が出ます。"
                + "キーボードのテーマは、写真の背景（キーの後ろが透ける）にしてください。"
                + "単色のテーマでは、動画や画像がキーの下に隠れます。動画は音なしで、くり返し再生します。");
        note.setPadding(0, dp(12), 0, 0);
        switchCard.addView(note);
        content.addView(switchCard);

        // One card for each screen: the file, and the part that is shown.
        previews = new Preview[2];
        String[] titles = {"閉じた画面（スマホの画面）", "開いた画面（広い画面）"};
        for (int mode = BackgroundSettings.MODE_SMALL; mode <= BackgroundSettings.MODE_LARGE; mode++) {
            final int screen = mode;
            LinearLayout screenCard = card();
            TextView screenTitle = new TextView(this);
            screenTitle.setText(titles[mode]);
            screenTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            screenTitle.setTextColor(palette.text);
            screenCard.addView(screenTitle);

            status[mode] = new TextView(this);
            status[mode].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            status[mode].setTextColor(palette.subText);
            status[mode].setPadding(0, dp(2), 0, dp(12));
            screenCard.addView(status[mode]);

            Button pick = tonalButton("動画か画像を選ぶ");
            pick.setOnClickListener(v -> pickMedia(screen));
            screenCard.addView(pick);

            final Preview preview = new Preview(this, mode, palette);
            previews[mode] = preview;
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            previewParams.topMargin = dp(16);
            screenCard.addView(preview, previewParams);

            TextView zoomLabel = label("拡大");
            zoomLabel.setPadding(0, dp(12), 0, 0);
            screenCard.addView(zoomLabel);

            SeekBar zoom = new SeekBar(this);
            zoom.setMax(100);
            zoom.setProgressTintList(ColorStateList.valueOf(palette.accent));
            zoom.setThumbTintList(ColorStateList.valueOf(palette.accent));
            preview.zoomBar = zoom;
            zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) {
                        preview.zoom = 1f + progress / 100f * (BackgroundSettings.MAX_ZOOM - 1f);
                        preview.invalidate();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    preview.save();
                }
            });
            screenCard.addView(zoom);

            TextView hint = label("指でずらして場所を決め、2本の指かバーで拡大します");
            screenCard.addView(hint);

            Button delete = tonalButton("この画面の動画・画像を消す");
            delete.setTextColor(palette.danger);
            delete.setOnClickListener(v -> {
                BackgroundSettings.deleteMedia(this, screen);
                loadState();
            });
            deleteButtons[mode] = delete;
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.topMargin = dp(16);
            screenCard.addView(delete, deleteParams);
            content.addView(screenCard);
        }

        // A card with what the keyboard and the theme builder did, to find out why a background does not show.
        LinearLayout logCard = card();
        logCard.addView(label("状態（うまく動かないときの調査用）"));
        logView = new TextView(this);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logView.setTextColor(palette.subText);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(8), 0, 0);
        logCard.addView(logView);
        content.addView(logCard);

        // The content is not wider than a phone, and in the middle on a large screen.
        FrameLayout holder = new FrameLayout(this);
        holder.addView(content, new FrameLayout.LayoutParams(contentWidth(), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        scroll.setFillViewport(true);
        scroll.addView(holder);
        // The screen is drawn under the system bars, so the content is moved out from under them.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return android.view.WindowInsets.CONSUMED;
        });
        setContentView(scroll);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewGroup.LayoutParams params = content.getLayoutParams();
        params.width = contentWidth();
        content.setLayoutParams(params);
        for (Preview preview : previews) {
            preview.load();
        }
    }

    private int contentWidth() {
        return Math.min(getResources().getDisplayMetrics().widthPixels, dp(MAX_CONTENT_DP));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.card);
        background.setCornerRadius(dp(28));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(palette.subText);
        return view;
    }

    private Button tonalButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTextColor(palette.onAccentContainer);
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.accentContainer);
        background.setCornerRadius(dp(100));
        button.setBackground(background);
        button.setStateListAnimator(null);
        return button;
    }

    /**
     * The colors of the screen. They follow the colors of the system (Material You) and the dark mode, so the screen
     * looks like the settings of Gboard.
     */
    private static final class Palette {
        final int background;
        final int card;
        final int text;
        final int subText;
        final int accent;
        final int accentContainer;
        final int onAccentContainer;
        final int danger;

        Palette(Context context) {
            boolean night = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            boolean dynamic = Build.VERSION.SDK_INT >= 31;

            background = night ? color(context, dynamic, android.R.color.system_neutral1_900, 0xFF131314)
                    : color(context, dynamic, android.R.color.system_neutral1_10, 0xFFF4F5F7);
            card = night ? color(context, dynamic, android.R.color.system_neutral2_800, 0xFF212224)
                    : color(context, dynamic, android.R.color.system_neutral1_0, 0xFFFFFFFF);
            text = night ? color(context, dynamic, android.R.color.system_neutral1_100, 0xFFE6E6E9)
                    : color(context, dynamic, android.R.color.system_neutral1_900, 0xFF1B1B1F);
            subText = night ? color(context, dynamic, android.R.color.system_neutral2_300, 0xFFC4C6D0)
                    : color(context, dynamic, android.R.color.system_neutral2_600, 0xFF5E6064);
            accent = night ? color(context, dynamic, android.R.color.system_accent1_200, 0xFFA8C7FA)
                    : color(context, dynamic, android.R.color.system_accent1_600, 0xFF0B57D0);
            accentContainer = night ? color(context, dynamic, android.R.color.system_accent1_700, 0xFF0842A0)
                    : color(context, dynamic, android.R.color.system_accent1_100, 0xFFD3E3FD);
            onAccentContainer = night ? color(context, dynamic, android.R.color.system_accent1_100, 0xFFD3E3FD)
                    : color(context, dynamic, android.R.color.system_accent1_900, 0xFF041E49);
            danger = night ? 0xFFF2B8B5 : 0xFFB3261E;
        }

        private static int color(Context context, boolean dynamic, int resource, int fallback) {
            try {
                return dynamic ? context.getColor(resource) : fallback;
            } catch (Exception ex) {
                return fallback;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadState();
    }

    private void pickMedia(int screen) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        startActivityForResult(intent, REQUEST_PICK + screen);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode < REQUEST_PICK || requestCode > REQUEST_PICK + BackgroundSettings.MODE_LARGE
                || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        final int screen = requestCode - REQUEST_PICK;
        final Uri uri = data.getData();
        status[screen].setText("コピーしています…");
        new Thread(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                int type = mime != null && mime.startsWith("video/")
                        ? BackgroundSettings.TYPE_VIDEO : BackgroundSettings.TYPE_IMAGE;
                String name = displayName(uri);

                File file = BackgroundSettings.mediaFile(this, screen);
                File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                file.delete();
                if (!temp.renameTo(file)) {
                    throw new IllegalStateException("rename failed");
                }

                BackgroundSettings.saveMedia(this, screen, type, name);
                runOnUiThread(this::loadState);
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "読み込めませんでした: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    loadState();
                });
            }
        }, "GboardBackgroundCopy").start();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
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

    /** Shows the saved settings, and the first frame of the media. */
    private void loadState() {
        BackgroundSettings.migrate(this);

        String log = BackgroundSettings.logText(this);
        logView.setText(log.isEmpty() ? "（まだありません）" : log);

        boolean any = BackgroundSettings.hasAnyMedia(this);
        updating = true;
        enabledSwitch.setChecked(any && BackgroundSettings.isEnabled(this));
        enabledSwitch.setEnabled(any);
        updating = false;

        for (int mode = BackgroundSettings.MODE_SMALL; mode <= BackgroundSettings.MODE_LARGE; mode++) {
            final int screen = mode;
            final int slot = BackgroundSettings.resolveSlot(this, mode);
            final Preview preview = previews[mode];

            String text;
            if (slot < 0) {
                text = "動画も画像も選んでいません";
            } else {
                String kind = BackgroundSettings.slotType(this, slot) == BackgroundSettings.TYPE_VIDEO
                        ? "動画: " : "画像: ";
                text = slot == mode ? kind + BackgroundSettings.slotName(this, slot)
                        : "この画面用は未選択（もう一方と同じ: " + BackgroundSettings.slotName(this, slot) + "）";
            }
            status[mode].setText(text);
            deleteButtons[mode].setEnabled(BackgroundSettings.hasMedia(this, mode));

            preview.load();
            preview.setFrame(null);
            if (slot < 0) {
                continue;
            }

            final int type = BackgroundSettings.slotType(this, slot);
            final File file = BackgroundSettings.mediaFile(this, slot);
            new Thread(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = type == BackgroundSettings.TYPE_VIDEO ? videoFrame(file) : imageFrame(file);
                } catch (Exception ex) {
                    android.util.Log.e("GboardBackground", "Could not make the preview", ex);
                }
                final Bitmap result = bitmap;
                runOnUiThread(() -> {
                    if (result == null) {
                        status[screen].setText(status[screen].getText()
                                + "\n（プレビューを作れませんでした。キーボードには出るかもしれません）");
                    }
                    preview.setFrame(result);
                });
            }, "GboardBackgroundFrame").start();
        }
    }

    private static Bitmap videoFrame(File file) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getPath());
            Bitmap bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime();
            }
            return bitmap;
        } finally {
            retriever.release();
        }
    }

    private static Bitmap imageFrame(File file) throws Exception {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, source) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            Size size = info.getSize();
            int longest = Math.max(size.getWidth(), size.getHeight());
            if (longest > PREVIEW_SIDE) {
                float factor = PREVIEW_SIDE / (float) longest;
                decoder.setTargetSize(Math.round(size.getWidth() * factor), Math.round(size.getHeight() * factor));
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * The keyboard background of one screen: drag to move, pinch to zoom.
     */
    private static final class Preview extends View {
        private final int mode;
        private final BackgroundTransform transform = new BackgroundTransform();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector scaleDetector;

        float centerX = 0.5f;
        float centerY = 0.5f;
        float zoom = 1f;
        SeekBar zoomBar;
        private Bitmap bitmap;
        private float aspect = 1.3f;
        private float lastX;
        private float lastY;

        Preview(Context context, int mode, Palette palette) {
            super(context);
            this.mode = mode;
            setBackgroundColor(Color.argb(40, 128, 128, 128));
            final float radius = 16 * context.getResources().getDisplayMetrics().density;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
            setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    zoom = Math.max(1f, Math.min(BackgroundSettings.MAX_ZOOM, zoom * detector.getScaleFactor()));
                    updateBar();
                    invalidate();
                    return true;
                }
            });
        }

        void load() {
            Context context = getContext();
            centerX = BackgroundSettings.centerX(context, mode);
            centerY = BackgroundSettings.centerY(context, mode);
            zoom = BackgroundSettings.zoom(context, mode);
            aspect = BackgroundSettings.aspect(context, mode);
            updateBar();
            requestLayout();
            invalidate();
        }

        void setFrame(Bitmap frame) {
            bitmap = frame;
            invalidate();
        }

        void save() {
            BackgroundSettings.saveView(getContext(), mode, centerX, centerY, zoom);
        }

        private void updateBar() {
            if (zoomBar != null) {
                zoomBar.setProgress(Math.round((zoom - 1f) / (BackgroundSettings.MAX_ZOOM - 1f) * 100f));
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            setMeasuredDimension(width, Math.round(width / aspect));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap != null) {
                transform.compute(bitmap.getWidth(), bitmap.getHeight(), getWidth(), getHeight(),
                        centerX, centerY, zoom);
                centerX = transform.centerX;
                centerY = transform.centerY;
                canvas.save();
                canvas.translate(transform.left, transform.top);
                canvas.scale(transform.scale, transform.scale);
                canvas.drawBitmap(bitmap, 0, 0, paint);
                canvas.restore();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null) {
                return false;
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                        // Dragging moves the picture, so the center goes the other way.
                        float width = bitmap.getWidth() * transform.scale;
                        float height = bitmap.getHeight() * transform.scale;
                        centerX -= (event.getX() - lastX) / width;
                        centerY -= (event.getY() - lastY) / height;
                        invalidate();
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    save();
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
