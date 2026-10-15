package app.re0.extension.gboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import java.io.File;

/**
 * Shows the video or the image of the custom background, with the part that is set for the screen.
 * <p>
 * Everything is loaded when the keyboard is shown, and released when it is hidden or removed. The video
 * plays without sound, in a loop, only while the keyboard is shown. A GIF or an animated WebP plays the same way.
 */
final class BackgroundMediaView extends FrameLayout implements TextureView.SurfaceTextureListener {
    private static final String TAG = "GboardBackground";

    /** The longest side of a decoded image, so a large photo does not use too much memory. */
    private static final int MAX_IMAGE_SIDE = 4096;

    private final BackgroundTransform transform = new BackgroundTransform();
    private final Matrix matrix = new Matrix();

    private long loadedVersion = -1;
    private int loadedType = BackgroundSettings.TYPE_NONE;

    private boolean playing;
    private int mediaWidth;
    private int mediaHeight;

    private Drawable image;
    private TextureView textureView;
    private MediaPlayer player;
    private Surface surface;
    private boolean prepared;

    BackgroundMediaView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        playing = getWindowVisibility() == VISIBLE;
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
        loadedVersion = -1;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        playing = visibility == VISIBLE;
        if (playing) {
            refresh();
            play();
        } else {
            pause();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            int width = right - left;
            int height = bottom - top;
            if (width > 0 && height > 0) {
                rememberAspect(width, height);
                applyTransform();
            }
        }
    }

    /** The picker shows the same shape of the keyboard as the one on the screen. */
    private void rememberAspect(int width, int height) {
        try {
            int mode = BackgroundSettings.modeOf(getContext());
            float aspect = width / (float) height;
            if (Math.abs(aspect - BackgroundSettings.aspect(getContext(), mode)) > 0.02f) {
                BackgroundSettings.saveAspect(getContext(), mode, aspect);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Could not save the aspect", ex);
        }
    }

    /**
     * Loads the media again if the settings changed since it was loaded.
     */
    private void refresh() {
        try {
            Context context = getContext();
            boolean enabled = BackgroundSettings.isEnabled(context);
            int type = BackgroundSettings.type(context);
            File file = BackgroundSettings.mediaFile(context);
            if (!enabled || type == BackgroundSettings.TYPE_NONE || !file.isFile()) {
                if (loadedType != BackgroundSettings.TYPE_NONE) {
                    release();
                }
                loadedVersion = -1;
                return;
            }

            long version = BackgroundSettings.version(context);
            if (version == loadedVersion && type == loadedType) {
                applyTransform();
                return;
            }

            release();
            loadedVersion = version;
            loadedType = type;
            if (type == BackgroundSettings.TYPE_VIDEO) {
                loadVideo();
            } else {
                loadImage(file, version);
            }
        } catch (Exception ex) {
            Log.e(TAG, "refresh failure", ex);
        }
    }

    // region Image

    private void loadImage(final File file, final long version) {
        new Thread(() -> {
            try {
                final Drawable decoded = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),
                        (decoder, info, source) -> {
                            Size size = info.getSize();
                            int longest = Math.max(size.getWidth(), size.getHeight());
                            if (longest > MAX_IMAGE_SIDE) {
                                float factor = MAX_IMAGE_SIDE / (float) longest;
                                decoder.setTargetSize(Math.round(size.getWidth() * factor),
                                        Math.round(size.getHeight() * factor));
                            }
                        });
                post(() -> {
                    if (version != loadedVersion) {
                        return;
                    }
                    image = decoded;
                    mediaWidth = decoded.getIntrinsicWidth();
                    mediaHeight = decoded.getIntrinsicHeight();
                    decoded.setCallback(this);
                    if (decoded instanceof AnimatedImageDrawable) {
                        ((AnimatedImageDrawable) decoded).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    }
                    play();
                    invalidate();
                });
            } catch (Exception ex) {
                Log.e(TAG, "Could not decode the image", ex);
            }
        }, "GboardBackgroundImage").start();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == image || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (image == null || mediaWidth <= 0 || mediaHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        compute();
        canvas.save();
        canvas.translate(transform.left, transform.top);
        canvas.scale(transform.scale, transform.scale);
        image.setBounds(0, 0, mediaWidth, mediaHeight);
        image.draw(canvas);
        canvas.restore();
    }

    // endregion

    // region Video

    private void loadVideo() {
        textureView = new TextureView(getContext());
        textureView.setOpaque(false);
        textureView.setSurfaceTextureListener(this);
        addView(textureView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        startPlayer(texture);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        applyTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

    private void startPlayer(SurfaceTexture texture) {
        releasePlayer();
        try {
            surface = new Surface(texture);
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(BackgroundSettings.mediaFile(getContext()).getPath());
            mediaPlayer.setSurface(surface);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (width > 0 && height > 0) {
                    mediaWidth = width;
                    mediaHeight = height;
                    applyTransform();
                }
            });
            mediaPlayer.setOnPreparedListener(mp -> {
                prepared = true;
                if (playing) {
                    mp.start();
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "The video failed: " + what + " " + extra);
                releasePlayer();
                return true;
            });
            mediaPlayer.prepareAsync();
            player = mediaPlayer;
        } catch (Exception ex) {
            Log.e(TAG, "Could not start the video", ex);
            releasePlayer();
        }
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    // endregion

    private void compute() {
        Context context = getContext();
        int mode = BackgroundSettings.modeOf(context);
        transform.compute(mediaWidth, mediaHeight, getWidth(), getHeight(),
                BackgroundSettings.centerX(context, mode), BackgroundSettings.centerY(context, mode),
                BackgroundSettings.zoom(context, mode));
    }

    private void applyTransform() {
        if (mediaWidth <= 0 || mediaHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        if (textureView != null) {
            compute();
            // The texture is stretched over the view first, and then the matrix works on that.
            matrix.setScale(mediaWidth * transform.scale / getWidth(), mediaHeight * transform.scale / getHeight());
            matrix.postTranslate(transform.left, transform.top);
            textureView.setTransform(matrix);
        } else {
            invalidate();
        }
    }

    private void play() {
        if (!playing) {
            return;
        }
        if (player != null && prepared && !player.isPlaying()) {
            player.start();
        }
        if (image instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) image).start();
        }
    }

    private void pause() {
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
        if (image instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) image).stop();
        }
    }

    private void release() {
        releasePlayer();
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            removeView(textureView);
            textureView = null;
        }
        if (image != null) {
            image.setCallback(null);
            if (image instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) image).stop();
            }
            image = null;
        }
        mediaWidth = 0;
        mediaHeight = 0;
        loadedType = BackgroundSettings.TYPE_NONE;
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Not interested in touches, the keys are in front. */
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }
}
