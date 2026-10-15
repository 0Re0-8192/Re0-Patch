package app.re0.extension.gboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
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
    private static final int REQUEST_PICK = 1;
    private static final int PREVIEW_SIDE = 1600;

    private TextView status;
    private Switch enabledSwitch;
    private Preview[] previews;
    private Bitmap frame;
    private boolean updating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("背景の動画・画像");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(15);
        root.addView(status);

        Button pick = new Button(this);
        pick.setText("動画か画像を選ぶ");
        pick.setOnClickListener(v -> pickMedia());
        root.addView(pick);

        enabledSwitch = new Switch(this);
        enabledSwitch.setText("背景に表示する");
        enabledSwitch.setOnCheckedChangeListener((CompoundButton button, boolean checked) -> {
            if (!updating) {
                BackgroundSettings.saveEnabled(this, checked);
            }
        });
        root.addView(enabledSwitch);

        TextView note = new TextView(this);
        note.setTextSize(12);
        note.setText("キーボードのテーマは、写真の背景（キーの後ろが透ける）にしてください。"
                + "単色のテーマでは、動画や画像がキーの下に隠れます。動画は音なしで、くり返し再生します。");
        root.addView(note);

        previews = new Preview[2];
        String[] titles = {"閉じた画面（スマホの画面）", "開いた画面（広い画面）"};
        for (int mode = BackgroundSettings.MODE_SMALL; mode <= BackgroundSettings.MODE_LARGE; mode++) {
            TextView title = new TextView(this);
            title.setText(titles[mode]);
            title.setTextSize(16);
            title.setPadding(0, dp(20), 0, dp(4));
            root.addView(title);

            previews[mode] = new Preview(this, mode);
            root.addView(previews[mode]);

            final Preview preview = previews[mode];
            SeekBar zoom = new SeekBar(this);
            zoom.setMax(100);
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
            root.addView(zoom);

            TextView hint = new TextView(this);
            hint.setTextSize(12);
            hint.setText("指でずらして、2本の指かバーで拡大します");
            root.addView(hint);
        }

        Button delete = new Button(this);
        delete.setText("動画・画像を消す");
        delete.setOnClickListener(v -> {
            BackgroundSettings.mediaFile(this).delete();
            BackgroundSettings.saveMedia(this, BackgroundSettings.TYPE_NONE, "");
            loadState();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deleteParams.topMargin = dp(24);
        root.addView(delete, deleteParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
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
    protected void onResume() {
        super.onResume();
        loadState();
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        startActivityForResult(intent, REQUEST_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        final Uri uri = data.getData();
        status.setText("コピーしています…");
        new Thread(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                int type = mime != null && mime.startsWith("video/")
                        ? BackgroundSettings.TYPE_VIDEO : BackgroundSettings.TYPE_IMAGE;
                String name = displayName(uri);

                File file = BackgroundSettings.mediaFile(this);
                File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                if (!temp.renameTo(file)) {
                    throw new IllegalStateException("rename failed");
                }

                BackgroundSettings.saveMedia(this, type, name);
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
        final int type = BackgroundSettings.type(this);
        final File file = BackgroundSettings.mediaFile(this);
        final boolean has = type != BackgroundSettings.TYPE_NONE && file.isFile();

        updating = true;
        enabledSwitch.setChecked(has && BackgroundSettings.isEnabled(this));
        enabledSwitch.setEnabled(has);
        updating = false;

        status.setText(has
                ? (type == BackgroundSettings.TYPE_VIDEO ? "動画: " : "画像: ") + BackgroundSettings.name(this)
                : "動画も画像も選んでいません");

        for (Preview preview : previews) {
            preview.load();
        }

        frame = null;
        if (!has) {
            for (Preview preview : previews) {
                preview.setFrame(null);
            }
            return;
        }

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
                    status.setText(status.getText() + "\n（プレビューを作れませんでした。キーボードには出るかもしれません）");
                }
                frame = result;
                for (Preview preview : previews) {
                    preview.setFrame(result);
                }
            });
        }, "GboardBackgroundFrame").start();
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
        private final Paint border = new Paint();
        private final ScaleGestureDetector scaleDetector;

        float centerX = 0.5f;
        float centerY = 0.5f;
        float zoom = 1f;
        SeekBar zoomBar;
        private Bitmap bitmap;
        private float aspect = 1.3f;
        private float lastX;
        private float lastY;

        Preview(Context context, int mode) {
            super(context);
            this.mode = mode;
            border.setColor(Color.GRAY);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(4f);
            setBackgroundColor(Color.DKGRAY);
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
            canvas.drawRect(0, 0, getWidth(), getHeight(), border);
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
