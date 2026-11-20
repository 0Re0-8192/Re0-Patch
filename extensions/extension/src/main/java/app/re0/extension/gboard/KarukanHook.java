package app.re0.extension.gboard;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Japanese input by karukan instead of Mozc.
 * <p>
 * The patch replaces the call {@code MozcJni.evalCommand(bytes)} in Gboard with
 * {@link #evalCommand(byte[])}. The bytes are a serialized Mozc {@code Command}. The karukan
 * engine answers the typing of romaji (a composition belongs to one engine from its first key
 * until it is empty again). Everything else, the other keyboards, and the case that karukan
 * is not set up, go to Mozc, so Gboard keeps working.
 * <p>
 * Files are in the app's external files directory, {@code Android/data/<package>/files/karukan}:
 * <pre>
 * models/small/jinen-v2-small-Q8_0.gguf + tokenizer.json   (main model)
 * models/xsmall/jinen-v2-xsmall-Q8_0.gguf + tokenizer.json (light model)
 * data/dict.bin                                            (system dictionary, optional)
 * config/config.toml                                       (written on the first run)
 * dump.flag                                                (create to log every command to logcat)
 * off.flag                                                 (create to turn karukan off)
 * </pre>
 */
@SuppressWarnings("unused")
public final class KarukanHook {
    private static final String TAG = "GboardKarukan";
    private static final String MOZC_JNI = "com.google.android.apps.inputmethod.libs.mozc.session.MozcJni";

    /** 0: not tried yet, 1: running, -1: not available. */
    private static int state;
    private static Method mozcEval;
    private static File root;

    private KarukanHook() {
    }

    public static byte[] evalCommand(byte[] command) {
        byte[] reply = null;
        if (state >= 0) {
            synchronized (KarukanHook.class) {
                if (state == 0) {
                    setUp();
                }
            }
        }
        if (state > 0 && !new File(root, "off.flag").exists()) {
            try {
                reply = KarukanNative.handle(command);
            } catch (Throwable t) {
                fail("handle", t);
            }
        }
        boolean dump = state > 0 && new File(root, "dump.flag").exists();
        if (dump) {
            log("in ", command);
        }
        if (reply == null) {
            reply = callMozc(command);
            if (state > 0) {
                try {
                    byte[] amended = KarukanNative.observe(command, reply);
                    if (amended != null) {
                        reply = amended;
                    }
                } catch (Throwable t) {
                    fail("observe", t);
                }
            }
            if (dump) {
                log("mozc", reply);
            }
        } else if (dump) {
            log("karukan", reply);
        }
        return reply;
    }

    private static byte[] callMozc(byte[] command) {
        try {
            if (mozcEval == null) {
                mozcEval = Class.forName(MOZC_JNI).getMethod("evalCommand", byte[].class);
            }
            return (byte[]) mozcEval.invoke(null, (Object) command);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static void setUp() {
        state = -1;
        try {
            Context context = AppContext.get();
            if (context == null) {
                return;
            }
            File base = context.getExternalFilesDir(null);
            if (base == null) {
                base = context.getFilesDir();
            }
            root = new File(base, "karukan");
            File small = new File(root, "models/small/jinen-v2-small-Q8_0.gguf");
            File xsmall = new File(root, "models/xsmall/jinen-v2-xsmall-Q8_0.gguf");
            if (!small.isFile() && !xsmall.isFile()) {
                Log.i(TAG, "No model in " + root + ", Mozc is used");
                return;
            }
            File config = new File(root, "config");
            File data = new File(root, "data");
            config.mkdirs();
            data.mkdirs();
            File toml = new File(config, "config.toml");
            if (!toml.exists()) {
                writeConfig(toml, small, xsmall);
            }
            System.load(installLibrary(context).getAbsolutePath());
            KarukanNative.init(data.getAbsolutePath(), config.getAbsolutePath());
            state = 1;
            Log.i(TAG, "karukan is on, files in " + root);
        } catch (Throwable t) {
            fail("setUp", t);
        }
    }

    /**
     * Copies the native library out of the assets (the patch put it there) when this build of the
     * app has not copied it yet. The file is read only, as Android requires for loaded code.
     */
    private static File installLibrary(Context context) throws Exception {
        File lib = new File(context.getFilesDir(), "libkarukan_jni.so");
        File stamp = new File(context.getFilesDir(), "libkarukan_jni.stamp");
        String version = String.valueOf(
                context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime);
        String copied = stamp.isFile() ? new String(java.nio.file.Files.readAllBytes(stamp.toPath()),
                StandardCharsets.UTF_8) : "";
        if (!lib.isFile() || !version.equals(copied)) {
            //noinspection ResultOfMethodCallIgnored
            lib.setWritable(true);
            //noinspection ResultOfMethodCallIgnored
            lib.delete();
            try (java.io.InputStream in = context.getAssets().open("karukan_jni.so");
                 FileOutputStream out = new FileOutputStream(lib)) {
                byte[] buffer = new byte[1 << 16];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
            }
            //noinspection ResultOfMethodCallIgnored
            lib.setReadOnly();
            try (FileOutputStream out = new FileOutputStream(stamp)) {
                out.write(version.getBytes(StandardCharsets.UTF_8));
            }
        }
        return lib;
    }

    private static void writeConfig(File toml, File small, File xsmall) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("[models]\n");
        String main = small.isFile() ? "jinen-small" : "jinen-xsmall";
        if (small.isFile()) {
            sb.append("jinen-small = \"").append(small.getAbsolutePath()).append("\"\n");
        }
        if (xsmall.isFile()) {
            sb.append("jinen-xsmall = \"").append(xsmall.getAbsolutePath()).append("\"\n");
        }
        sb.append("\n[conversion]\n");
        sb.append("model = \"").append(main).append("\"\n");
        sb.append("light_model = \"").append(xsmall.isFile() ? "jinen-xsmall" : main).append("\"\n");
        sb.append("n_threads = 4\n");
        try (FileOutputStream out = new FileOutputStream(toml)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void fail(String where, Throwable t) {
        state = -1;
        Log.e(TAG, "karukan is off after an error in " + where, t);
    }

    private static void log(String tag, byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes == null ? 4 : bytes.length * 2);
        if (bytes == null) {
            sb.append("null");
        } else {
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
        }
        String s = sb.toString();
        for (int i = 0; i < s.length(); i += 3000) {
            Log.d(TAG, tag + " " + s.substring(i, Math.min(s.length(), i + 3000)));
        }
    }
}
