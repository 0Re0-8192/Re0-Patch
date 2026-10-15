package app.re0.patches.gboard.clipboard

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

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
