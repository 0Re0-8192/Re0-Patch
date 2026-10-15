package app.re0.patches.gboard.clipboard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * ClipboardContentProviderUtils.getUnpinnedItemTimeLimitInMilliSeconds(Context).
 * Unpinned clips older than this are deleted, and not shown.
 */
internal object UnpinnedItemTimeLimitFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "J",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("getUnpinnedItemTimeLimitInMilliSeconds"),
)

/**
 * ClipboardContentProviderUtils method that loads the clips the clipboard screen shows:
 *
 * ```
 * long threshold = Math.max(now - getUnpinnedItemTimeLimit(context), prefs.getLong(UNPINNED_ITEM_THRESHOLD_TIME));
 * List<Clip> clips = query("... timestamp >= ?", threshold);
 * ...
 * for (Clip clip : recentClips) {
 *     if (count >= limit) break;
 *     if (lastTimestamp == null || !clip.timestamp.equals(lastTimestamp)) {
 *         if (copies >= 5) break;
 *         lastTimestamp = clip.timestamp;
 *         copies++;
 *     }
 *     shown.add(clip);
 * }
 * prefs.putLong(UNPINNED_ITEM_THRESHOLD_TIME, shown.last().timestamp);
 * ```
 */
internal object ClipboardItemsQueryFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;", "Z"),
    strings = listOf("(%s OR %s OR %s)"),
    filters = listOf(
        // Math.max(now - time limit, saved threshold).
        methodCall(definingClass = "Ljava/lang/Math;", name = "max", returnType = "J"),
        // Clip timestamp compared with the previous one.
        methodCall(definingClass = "/Instant;", name = "equals"),
        // if (copies >= 5) break;
        opcode(Opcode.IF_GE),
    ),
)
