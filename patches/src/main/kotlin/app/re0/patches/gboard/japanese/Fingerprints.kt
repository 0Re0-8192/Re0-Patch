package app.re0.patches.gboard.japanese

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * LocalSessionHandler.evalCommand(Command): Command. The only place that sends a command to Mozc,
 * the Japanese engine, as serialized bytes:
 *
 * ```
 * byte[] output = MozcJni.evalCommand(command.toByteArray());
 * ```
 */
internal object EvalCommandFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    strings = listOf("LocalSessionHandler.evalCommand"),
    filters = listOf(
        methodCall(definingClass = "/MozcJni;", name = "evalCommand"),
    ),
)
