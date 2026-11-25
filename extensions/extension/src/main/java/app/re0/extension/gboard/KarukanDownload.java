package app.re0.extension.gboard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

/**
 * Fetches the models and the dictionary on the first run, so the patch alone is enough: no adb, no PC.
 * <p>
 * Runs once per process on a background thread, only on an unmetered network (about 360 MB; a
 * {@code download_metered.flag} allows any network), resumes a
 * partial file, and puts a file in place only when its SHA-256 matches. Mozc answers until everything is
 * there; then karukan is set up again. Every source is pinned (a model revision, a release tag), so what
 * arrives is exactly what was tested.
 */
final class KarukanDownload {
    private static final String TAG = "GboardKarukan";

    private static final class Item {
        final String path;   // under the karukan directory
        final String url;
        final String sha256; // of the file put in place
        final boolean tarGz; // the url is a .tgz whose first entry is the file

        Item(String path, String url, String sha256, boolean tarGz) {
            this.path = path;
            this.url = url;
            this.sha256 = sha256;
            this.tarGz = tarGz;
        }
    }

    private static final String SMALL = "https://huggingface.co/OReOOreoaaaa/jinen-v2-small-karukan.gguf/resolve/c02dc9bdd408b3b33491428ab9d3c211929f8c67/";
    private static final String XSMALL = "https://huggingface.co/togatogah/jinen-v2-xsmall.gguf/resolve/3910fd01bf4ba86eca89617f18db9b0c1c5b2283/";

    /** The main model's file; its name carries the release, so a new release is fetched next to an old one. */
    static final String SMALL_PATH = "models/small/jinen-v2-small-karukan-r9b-Q8_0.gguf";
    /** What adb-pushed installs and earlier releases used; deleted once {@link #SMALL_PATH} is in place. */
    static final String LEGACY_SMALL_PATH = "models/small/jinen-v2-small-Q8_0.gguf";

    private static final Item[] ITEMS = {
            new Item(SMALL_PATH, SMALL + "jinen-v2-small-karukan-r9b-Q8_0.gguf",
                    "0ec64db059dde94e9f56803bda2593c8e1922d9e89ac255d585ee1af9beac60a", false),
            new Item("models/small/tokenizer.json", SMALL + "tokenizer.json",
                    "477c91a788bf97e7ed993a4441cfb2c4d60033e505ade17d6c367bc09ed6e6ba", false),
            new Item("models/xsmall/jinen-v2-xsmall-Q8_0.gguf", XSMALL + "jinen-v2-xsmall-Q8_0.gguf",
                    "bc75a02512bdbb2a4786fec281a57d4d58ac3c2f118c3a29c2a9b41f2a4898b7", false),
            new Item("models/xsmall/tokenizer.json", XSMALL + "tokenizer.json",
                    "477c91a788bf97e7ed993a4441cfb2c4d60033e505ade17d6c367bc09ed6e6ba", false),
            new Item("data/dict.bin", "https://github.com/togatoga/karukan/releases/download/v0.1.0/dict.tgz",
                    "d85fed3c7e408e67f4a5dbe2314338366ed6183e0498d2726bc5a4dcaf5bb83b", true),
    };

    private static final long RETRY_MS = 10 * 60 * 1000L;
    private static boolean started;

    private KarukanDownload() {
    }

    /** True when every file is in place. */
    static boolean complete(File root) {
        for (Item item : ITEMS) {
            if (!new File(root, item.path).isFile()) {
                return false;
            }
        }
        return true;
    }

    /** Starts the background fetch once per process; {@code done} runs when every file is in place. */
    static synchronized void start(Context context, File root, Runnable done) {
        if (started || new File(root, "nodownload.flag").exists()) {
            return;
        }
        started = true;
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    if (unmetered(context) || new File(root, "download_metered.flag").exists()) {
                        KarukanNotice.show(KarukanNotice.FETCHING);
                        for (Item item : ITEMS) {
                            fetch(root, item);
                        }
                        //noinspection ResultOfMethodCallIgnored
                        new File(root, LEGACY_SMALL_PATH).delete();
                        Log.i(TAG, "models and dictionary are in place");
                        done.run();
                        KarukanNotice.show(KarukanNotice.READY);
                        return;
                    }
                    Log.i(TAG, "waiting for an unmetered network to fetch the models");
                    KarukanNotice.show(KarukanNotice.WAITING);
                } catch (Throwable e) {
                    Log.w(TAG, "fetching the models failed, trying again later", e);
                    KarukanNotice.show(KarukanNotice.FAILED);
                }
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "karukan-download");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static boolean unmetered(Context context) {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            return cm != null && cm.getActiveNetwork() != null && !cm.isActiveNetworkMetered();
        } catch (SecurityException e) {
            return false;
        }
    }

    private static void fetch(File root, Item item) throws Exception {
        File target = new File(root, item.path);
        if (target.isFile()) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        target.getParentFile().mkdirs();
        File part = new File(target.getPath() + ".part");
        download(item.url, part);
        File file = part;
        if (item.tarGz) {
            file = new File(target.getPath() + ".untar");
            untarFirst(part, file);
        }
        String sha = sha256(file);
        if (!sha.equals(item.sha256)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw new IllegalStateException(item.path + ": sha256 " + sha + " is not " + item.sha256);
        }
        if (!file.renameTo(target)) {
            throw new IllegalStateException("could not move " + file + " to " + target);
        }
        //noinspection ResultOfMethodCallIgnored
        part.delete();
        Log.i(TAG, "fetched " + item.path);
    }

    /** Downloads to {@code part}, resuming what is already there. */
    private static void download(String url, File part) throws Exception {
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(30_000);
        c.setReadTimeout(60_000);
        c.setInstanceFollowRedirects(true);
        if (have > 0) {
            c.setRequestProperty("Range", "bytes=" + have + "-");
        }
        int code = c.getResponseCode();
        if (code == 416) { // already complete
            c.disconnect();
            return;
        }
        if (code != 200 && code != 206) {
            c.disconnect();
            throw new IllegalStateException(url + ": HTTP " + code);
        }
        if (code == 200) {
            have = 0; // the server ignored the range: start over
        }
        try (InputStream in = c.getInputStream(); RandomAccessFile out = new RandomAccessFile(part, "rw")) {
            out.setLength(have);
            out.seek(have);
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } finally {
            c.disconnect();
        }
    }

    /** Writes the first regular file of the .tgz {@code archive} to {@code out}. */
    private static void untarFirst(File archive, File out) throws Exception {
        try (InputStream in = new GZIPInputStream(new FileInputStream(archive), 1 << 16);
             FileOutputStream os = new FileOutputStream(out)) {
            byte[] header = new byte[512];
            while (readFully(in, header)) {
                long size = Long.parseLong(new String(header, 124, 12, "US-ASCII").replace("\0", "").trim(), 8);
                char type = (char) header[156];
                long padded = (size + 511) / 512 * 512;
                if (type == '0' || type == '\0') {
                    copy(in, os, size);
                    skip(in, padded - size);
                    return;
                }
                skip(in, padded);
            }
            throw new IllegalStateException(archive + ": no file in the archive");
        }
    }

    private static boolean readFully(InputStream in, byte[] b) throws Exception {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) {
                return false;
            }
            off += n;
        }
        return true;
    }

    private static void copy(InputStream in, FileOutputStream os, long size) throws Exception {
        byte[] buffer = new byte[1 << 16];
        while (size > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, size));
            if (n < 0) {
                throw new IllegalStateException("archive ended early");
            }
            os.write(buffer, 0, n);
            size -= n;
        }
    }

    private static void skip(InputStream in, long n) throws Exception {
        byte[] buffer = new byte[4096];
        while (n > 0) {
            int r = in.read(buffer, 0, (int) Math.min(buffer.length, n));
            if (r < 0) {
                return;
            }
            n -= r;
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
