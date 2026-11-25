package app.re0.extension.gboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Saves what a reinstall of Gboard would lose to a zip the user picks, and reads it back.
 * <p>
 * What goes in: the Gboard settings and the Re0 background (device-protected storage), the themes made by the user,
 * the word list, the clipboard, the emoji history, the learning of the English keyboards and of Mozc, and the
 * learning of karukan. What stays out is what Gboard or karukan fetch again by themselves: the language packs,
 * the models, the dictionaries, the caches, and the bookkeeping of the downloads (restoring it without the files
 * would tell Gboard a pack is there when it is not).
 * <p>
 * Reading back: the entries are unpacked next to their targets first, then moved over them at once, and the
 * process is killed, so Gboard cannot write its copy in memory back over them. It starts again with the files of
 * the backup.
 */
public final class BackupActivity extends Activity {
    private static final String TAG = "GboardBackup";
    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_IMPORT = 2;
    private static final String MANIFEST = "re0-backup.txt";
    private static final String STAGING = ".re0_restore";

    /** The places an entry can come from or go to, by the first part of its name in the zip. */
    private static final String DE = "de/";
    private static final String CE = "ce/";
    private static final String KARUKAN = "karukan/";

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The window is drawn under the system bars (and an action bar would cover the content), so the screen has
        // its own title and keeps out from under the bars.
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        int pad = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return android.view.WindowInsets.CONSUMED;
        });

        TextView title = new TextView(this);
        title.setText("設定の保存");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setPadding(0, 0, 0, dp(24));
        content.addView(title);

        Button export = new Button(this);
        export.setText("書き出す");
        export.setOnClickListener(v -> startExport());
        content.addView(export, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        Button restore = new Button(this);
        restore.setText("読み込む");
        restore.setOnClickListener(v -> startImport());
        LinearLayout.LayoutParams restoreParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        restoreParams.topMargin = dp(12);
        content.addView(restore, restoreParams);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(16), 0, 0);
        content.addView(status);

        setContentView(content);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void startExport() {
        String name = "gboard-re0-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".zip";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            status.setText("…");
            new Thread(() -> {
                String message;
                try {
                    int n = export(uri);
                    message = "書き出しました（" + n + "）";
                } catch (Exception ex) {
                    Log.e(TAG, "export failed", ex);
                    message = "失敗しました: " + ex.getMessage();
                }
                String m = message;
                runOnUiThread(() -> status.setText(m));
            }).start();
        } else if (requestCode == REQUEST_IMPORT) {
            new AlertDialog.Builder(this)
                    .setMessage("今の設定を上書きします")
                    .setPositiveButton("読み込む", (d, w) -> runImport(uri))
                    .setNegativeButton("キャンセル", null)
                    .show();
        }
    }

    private void runImport(Uri uri) {
        status.setText("…");
        new Thread(() -> {
            try {
                int n = restore(uri);
                runOnUiThread(() -> {
                    Toast.makeText(this, "読み込みました（" + n + "）", Toast.LENGTH_SHORT).show();
                    // Gboard must not write its settings in memory back over the restored files.
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        finishAffinity();
                        Process.killProcess(Process.myPid());
                    }, 600);
                });
            } catch (Exception ex) {
                Log.e(TAG, "import failed", ex);
                runOnUiThread(() -> status.setText("失敗しました: " + ex.getMessage()));
            }
        }).start();
    }

    // ---- what goes in ----

    private File deRoot() {
        return createDeviceProtectedStorageContext().getDataDir();
    }

    private File ceRoot() {
        return getApplicationContext().getDataDir();
    }

    private File karukanRoot() {
        File base = getApplicationContext().getExternalFilesDir(null);
        return base == null ? null : new File(base, "karukan");
    }

    /** Whether a path (relative to its root, with '/') under the given part of the zip is backed up. */
    static boolean included(String part, String rel) {
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("\\")) {
            return false;
        }
        for (String segment : rel.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.equals(STAGING)) {
                return false;
            }
        }
        switch (part) {
            case DE:
                return (rel.startsWith("shared_prefs/") && rel.endsWith(".xml") && rel.indexOf('/', 13) < 0)
                        || (rel.startsWith("files/re0_background") && rel.indexOf('/', 6) < 0);
            case CE:
                return (rel.startsWith("files/user_theme_package_") && rel.endsWith(".zip") && rel.indexOf('/', 6) < 0)
                        || rel.startsWith("files/personal/")
                        || rel.startsWith(".mozc/")
                        || isDatabase(rel, "PersonalDictionary.db")
                        || isDatabase(rel, "gboard_clipboard.db")
                        || isDatabase(rel, "expression-history.db");
            case KARUKAN:
                // The learning, the corrections and the words of the user; the dictionary and the models are
                // fetched again, and the config is written at every start.
                return rel.startsWith("data/") && !rel.equals("data/dict.bin") && !rel.endsWith(".part");
            default:
                return false;
        }
    }

    /** A database with its journal. Not its -shm: an index of the -wal that SQLite builds again. */
    private static boolean isDatabase(String rel, String name) {
        String path = "databases/" + name;
        return rel.equals(path) || rel.equals(path + "-wal") || rel.equals(path + "-journal");
    }

    private void collect(File root, File dir, String part, List<String[]> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String rel = root.toURI().relativize(child.toURI()).getPath();
            if (child.isDirectory()) {
                if (!child.getName().equals(STAGING) && !child.getName().equals("cache")
                        && !child.getName().equals("code_cache") && !child.getName().equals("models")) {
                    collect(root, child, part, out);
                }
            } else if (included(part, rel)) {
                out.add(new String[]{part, rel, child.getAbsolutePath()});
            }
        }
    }

    private int export(Uri uri) throws IOException {
        List<String[]> files = new ArrayList<>();
        collect(deRoot(), deRoot(), DE, files);
        collect(ceRoot(), ceRoot(), CE, files);
        File karukan = karukanRoot();
        if (karukan != null && karukan.isDirectory()) {
            collect(karukan, karukan, KARUKAN, files);
        }
        try (OutputStream raw = getContentResolver().openOutputStream(uri, "wt")) {
            if (raw == null) {
                throw new IOException("出力先を開けません");
            }
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
                zip.putNextEntry(new ZipEntry(MANIFEST));
                String manifest = "format=1\nversionCode="
                        + getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode() + "\n";
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                byte[] buffer = new byte[1 << 16];
                for (String[] f : files) {
                    zip.putNextEntry(new ZipEntry(f[0] + f[1]));
                    try (InputStream in = new FileInputStream(f[2])) {
                        int n;
                        while ((n = in.read(buffer)) > 0) {
                            zip.write(buffer, 0, n);
                        }
                    }
                    zip.closeEntry();
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
            throw new IOException(ex);
        }
        return files.size();
    }

    // ---- reading back ----

    private File rootOf(String part) {
        switch (part) {
            case DE:
                return deRoot();
            case CE:
                return ceRoot();
            case KARUKAN:
                return karukanRoot();
            default:
                return null;
        }
    }

    private int restore(Uri uri) throws IOException {
        // Unpack next to the targets, under a staging directory of each root, so a broken file stops it all
        // before anything is replaced.
        List<String[]> staged = new ArrayList<>();
        boolean manifest = false;
        for (String part : new String[]{DE, CE, KARUKAN}) {
            File root = rootOf(part);
            if (root != null) {
                deleteTree(new File(root, STAGING));
            }
        }
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) {
                throw new IOException("ファイルを開けません");
            }
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                byte[] buffer = new byte[1 << 16];
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.equals(MANIFEST)) {
                        manifest = true;
                        continue;
                    }
                    if (entry.isDirectory()) {
                        continue;
                    }
                    int slash = name.indexOf('/');
                    if (slash < 0) {
                        continue;
                    }
                    String part = name.substring(0, slash + 1);
                    String rel = name.substring(slash + 1);
                    File root = rootOf(part);
                    if (root == null || !included(part, rel)) {
                        continue;
                    }
                    File out = new File(new File(root, STAGING), rel);
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("作れません: " + rel);
                    }
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zip.read(buffer)) > 0) {
                            os.write(buffer, 0, n);
                        }
                    }
                    staged.add(new String[]{part, rel});
                }
            }
        }
        if (!manifest) {
            for (String part : new String[]{DE, CE, KARUKAN}) {
                File root = rootOf(part);
                if (root != null) {
                    deleteTree(new File(root, STAGING));
                }
            }
            throw new IOException("このアプリの書き出しではありません");
        }

        // Mozc's learning is one set (the history and its key): replace it whole.
        boolean mozc = false;
        for (String[] s : staged) {
            if (s[0].equals(CE) && s[1].startsWith(".mozc/")) {
                mozc = true;
                break;
            }
        }
        if (mozc) {
            deleteTree(new File(ceRoot(), ".mozc"));
        }
        // First everything that would be read along with a restored file, then the moves: a database's journal
        // in the backup must not be cleared after it has been moved in.
        for (String[] s : staged) {
            clearSidecars(new File(rootOf(s[0]), s[1]));
        }
        for (String[] s : staged) {
            File root = rootOf(s[0]);
            File target = new File(root, s[1]);
            File source = new File(new File(root, STAGING), s[1]);
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("置き換えられません: " + s[1]);
            }
            if (!source.renameTo(target)) {
                throw new IOException("置き換えられません: " + s[1]);
            }
        }
        for (String part : new String[]{DE, CE, KARUKAN}) {
            File root = rootOf(part);
            if (root != null) {
                deleteTree(new File(root, STAGING));
            }
        }
        return staged.size();
    }

    /** What would be read instead of, or along with, a restored file: a prefs backup, a database's journal. */
    private static void clearSidecars(File target) {
        String name = target.getName();
        File dir = target.getParentFile();
        if (dir == null) {
            return;
        }
        if (name.endsWith(".xml")) {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, name + ".bak").delete();
        } else if (name.endsWith(".db")) {
            for (String suffix : new String[]{"-wal", "-shm", "-journal"}) {
                //noinspection ResultOfMethodCallIgnored
                new File(dir, name + suffix).delete();
            }
        }
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteTree(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
