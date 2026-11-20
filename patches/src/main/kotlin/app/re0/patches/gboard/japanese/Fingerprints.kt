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

/**
 * GoogleInputMethodService.onCreate(): the input method service of Gboard. The patch hands the service to the
 * extension, which reads the text before the cursor from it, as the context of the conversion.
 */
internal object InputMethodServiceCreateFingerprint : Fingerprint(
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    strings = listOf("GoogleInputMethodService.onCreate"),
)
