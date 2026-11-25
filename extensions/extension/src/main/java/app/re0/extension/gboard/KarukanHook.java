package app.re0.extension.gboard;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.inputmethod.InputConnection;

import java.io.ByteArrayOutputStream;
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
 * until it is empty again). The other keyboards (flick, 12 keys) are composed by Mozc, and the
 * engine converts the kana Mozc composed: its candidates come before Mozc's. Everything else,
 * and the case that karukan is not set up, go to Mozc, so Gboard keeps working.
 * <p>
 * Files are in the app's external files directory, {@code Android/data/<package>/files/karukan}:
 * <pre>
 * models/small/jinen-v2-small-Q8_0.gguf + tokenizer.json   (main model)
 * models/xsmall/jinen-v2-xsmall-Q8_0.gguf + tokenizer.json (light model)
 * data/dict.bin                                            (system dictionary, optional)
 * config/config.toml                                       (written on the first run)
 * dump.flag                                                (create to log every command to logcat)
 * off.flag                                                 (create to turn karukan off)
 * nohybrid.flag                                            (create to leave out the Mozc candidates)
 * nodownload.flag                                          (create to keep the first-run fetch off)
 * download_metered.flag                                    (create to allow the fetch on mobile data)
 * keepconfig.flag                                          (create to keep a hand-edited config.toml)
 * </pre>
 * The models and the dictionary are fetched on the first run when they are missing ({@link KarukanDownload}).
 */
@SuppressWarnings("unused")
public final class KarukanHook {
    private static final String TAG = "GboardKarukan";
    private static final String MOZC_JNI = "com.google.android.apps.inputmethod.libs.mozc.session.MozcJni";

    /** 0: not tried yet, 1: running, -1: not available. */
    private static volatile int state; // read outside the lock; the download thread resets it
    private static Method mozcEval;
    private static File root;

    /** The input method service, for the text before the cursor. Set when the service is created. */
    private static volatile InputMethodService service;

    private KarukanHook() {
    }

    public static void setService(InputMethodService inputMethodService) {
        service = inputMethodService;
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
                byte[] withContext = withContext(command);
                if (withContext != command && new File(root, "dump.flag").exists()) {
                    log("ctx", withContext);
                }
                reply = KarukanNative.handle(withContext);
                if (reply != null && hybrid) {
                    reply = addMozcCandidates(command, reply);
                }
            } catch (Throwable t) {
                fail("handle", t);
            }
        }
        boolean dump = state > 0 && new File(root, "dump.flag").exists();
        if (dump) {
            log("in ", command);
        }
        if (reply == null) {
            if (state > 0 && hybrid) {
                clearMozcComposition(command);
            }
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

    /**
     * Hybrid: while karukan composes, Mozc gets the same keys in the background and its candidates (symbols,
     * emoji, ...) are added after karukan's. Off when the library is too old to have it, after an error in it,
     * or when {@code nohybrid.flag} exists.
     */
    private static volatile boolean hybrid;

    private static byte[] addMozcCandidates(byte[] command, byte[] reply) {
        try {
            byte[] shadow = KarukanNative.shadowCommand(command);
            if (shadow == null) {
                return reply;
            }
            byte[] merged = KarukanNative.shadowReply(callMozc(shadow));
            return merged != null ? merged : reply;
        } catch (Throwable t) {
            hybridOff();
            Log.e(TAG, "The Mozc candidates are off after an error", t);
            return reply;
        }
    }

    /**
     * The library is told too: it follows a flick composition only while the hybrid exchange runs, since that
     * exchange reverts Mozc's kana after a karukan candidate is committed.
     */
    private static void hybridOff() {
        hybrid = false;
        try {
            KarukanNative.setHybrid(false);
        } catch (Throwable ignored) {
            // the flag above is what matters here
        }
    }

    /** Before a command goes to Mozc for real: clears the composition that Mozc holds in the background. */
    private static void clearMozcComposition(byte[] command) {
        try {
            byte[] revert = KarukanNative.shadowRevert(command);
            if (revert != null) {
                callMozc(revert);
            }
        } catch (Throwable t) {
            hybridOff();
            Log.e(TAG, "The Mozc candidates are off after an error", t);
        }
    }

    /** How many characters before the cursor are given to the engine. The engine keeps the tail of it. */
    private static final int CONTEXT_LENGTH = 200;

    private static final int TYPE_SEND_KEY = 3;

    /**
     * Gboard sends the Mozc input with an empty context. The text that is already in the field before the cursor
     * is the context of the conversion, so it is read from the input connection and put into the context of the
     * input (Command.input.context.preceding_text). Only for a key that is sent, and only when the context is empty.
     * The original command is returned when anything is not as expected.
     */
    private static byte[] withContext(byte[] command) {
        try {
            InputMethodService ime = service;
            if (ime == null) {
                return command;
            }
            byte[] input = null;
            int inputStart = -1;
            int inputEnd = -1;
            int position = 0;
            while (position < command.length) {
                int fieldStart = position;
                long tag = readVarint(command, position);
                position = varintEnd(command, position);
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (wire == 0) {
                    position = varintEnd(command, position);
                } else if (wire == 2) {
                    int length = (int) readVarint(command, position);
                    position = varintEnd(command, position);
                    if (field == 1 && input == null) {
                        input = java.util.Arrays.copyOfRange(command, position, position + length);
                        inputStart = fieldStart;
                        inputEnd = position + length;
                    }
                    position += length;
                } else if (wire == 1) {
                    position += 8;
                } else if (wire == 5) {
                    position += 4;
                } else {
                    return command;
                }
            }
            if (input == null) {
                return command;
            }

            ByteArrayOutputStream rebuilt = new ByteArrayOutputStream(input.length + 64);
            boolean sendKey = false;
            boolean hasContext = false;
            position = 0;
            while (position < input.length) {
                int fieldStart = position;
                long tag = readVarint(input, position);
                position = varintEnd(input, position);
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                int end;
                if (wire == 0) {
                    long value = readVarint(input, position);
                    end = varintEnd(input, position);
                    if (field == 1 && value == TYPE_SEND_KEY) {
                        sendKey = true;
                    }
                } else if (wire == 2) {
                    int length = (int) readVarint(input, position);
                    int body = varintEnd(input, position);
                    end = body + length;
                    if (field == 6) {
                        if (length > 0) {
                            hasContext = true;
                        } else {
                            // An empty context is replaced below.
                            position = end;
                            continue;
                        }
                    }
                } else if (wire == 1) {
                    end = position + 8;
                } else if (wire == 5) {
                    end = position + 4;
                } else {
                    return command;
                }
                rebuilt.write(input, fieldStart, end - fieldStart);
                position = end;
            }
            if (!sendKey || hasContext) {
                return command;
            }

            InputConnection connection = ime.getCurrentInputConnection();
            CharSequence before = connection == null ? null : connection.getTextBeforeCursor(CONTEXT_LENGTH, 0);
            if (before == null || before.length() == 0) {
                return command;
            }
            byte[] text = before.toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream context = new ByteArrayOutputStream(text.length + 8);
            writeVarint(context, (1 << 3) | 2);
            writeVarint(context, text.length);
            context.write(text, 0, text.length);
            writeVarint(rebuilt, (6 << 3) | 2);
            writeVarint(rebuilt, context.size());
            rebuilt.write(context.toByteArray(), 0, context.size());

            byte[] newInput = rebuilt.toByteArray();
            ByteArrayOutputStream result = new ByteArrayOutputStream(command.length + newInput.length);
            result.write(command, 0, inputStart);
            writeVarint(result, (1 << 3) | 2);
            writeVarint(result, newInput.length);
            result.write(newInput, 0, newInput.length);
            result.write(command, inputEnd, command.length - inputEnd);
            return result.toByteArray();
        } catch (Throwable t) {
            Log.w(TAG, "No context", t);
            return command;
        }
    }

    private static long readVarint(byte[] bytes, int position) {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = bytes[position++];
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
    }

    private static int varintEnd(byte[] bytes, int position) {
        while ((bytes[position++] & 0x80) != 0) {
            // The end of the number is the first byte without the high bit.
        }
        return position;
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7fL) != 0) {
            out.write((int) ((value & 0x7f) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
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
            File small = new File(root, KarukanDownload.SMALL_PATH);
            if (!small.isFile()) {
                small = new File(root, KarukanDownload.LEGACY_SMALL_PATH); // pushed by adb, or an earlier release
            }
            File xsmall = new File(root, "models/xsmall/jinen-v2-xsmall-Q8_0.gguf");
            if (!KarukanDownload.complete(root)) {
                // fetch what is missing (Mozc answers meanwhile), then set up again
                KarukanDownload.start(context, root, KarukanHook::retrySetUp);
            }
            if (!small.isFile() && !xsmall.isFile()) {
                Log.i(TAG, "No model in " + root + " yet, Mozc is used");
                return;
            }
            File config = new File(root, "config");
            File data = new File(root, "data");
            config.mkdirs();
            data.mkdirs();
            File toml = new File(config, "config.toml");
            // written at every start from the files there are (a model fetched since the last start is
            // picked up); keepconfig.flag keeps a hand-edited one
            if (!toml.exists() || !new File(root, "keepconfig.flag").exists()) {
                writeConfig(toml, small, xsmall);
            }
            System.load(installLibrary(context).getAbsolutePath());
            KarukanNative.init(data.getAbsolutePath(), config.getAbsolutePath());
            try {
                hybrid = !new File(root, "nohybrid.flag").exists();
                KarukanNative.setHybrid(hybrid);
            } catch (UnsatisfiedLinkError e) {
                hybrid = false; // a library without the Mozc candidates
            }
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
        // A libkarukan_jni.so next to the models wins over the one in the app (for trying a new
        // build without patching again).
        File override = new File(root, "libkarukan_jni.so");
        String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime
                + (override.isFile() ? ":" + override.length() + ":" + override.lastModified() : "");
        String copied = stamp.isFile() ? new String(java.nio.file.Files.readAllBytes(stamp.toPath()),
                StandardCharsets.UTF_8) : "";
        if (!lib.isFile() || !version.equals(copied)) {
            //noinspection ResultOfMethodCallIgnored
            lib.setWritable(true);
            //noinspection ResultOfMethodCallIgnored
            lib.delete();
            try (java.io.InputStream in = override.isFile() ? new java.io.FileInputStream(override)
                    : context.getAssets().open("karukan_jni.so");
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

    /** After the first-run fetch: set up again, unless karukan already runs (then the new files wait for a restart). */
    static void retrySetUp() {
        synchronized (KarukanHook.class) {
            if (state < 0) {
                state = 0;
            }
        }
    }

    private static void writeConfig(File toml, File small, File xsmall) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# written by the karukan patch at every start (keepconfig.flag keeps a hand-edited one)\n");
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
        // the engine's default; written out so a library built before it was raised from 10 uses it too
        sb.append("context_chars = 25\n");
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
