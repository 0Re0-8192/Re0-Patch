package app.re0.patches.gboard.flags

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Matches a method of the flag implementation class
 * (com.google.android.libraries.inputmethod.flag, obfuscated).
 * Only used to find the class.
 */
internal object FlagImplClassFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("Clearing default value is disallowed ["),
    ),
)

/**
 * Getter of the final flag value, after Gboard resolved all flag sources:
 *
 * ```
 * public final Object g() {
 *     oeg resolved = this.f;
 *     if (resolved != null) return resolved.a;
 *     throw new IllegalStateException("Invalid flag: ".concat(toString()));
 * }
 * ```
 */
internal object FlagValueGetterFingerprint : Fingerprint(
    classFingerprint = FlagImplClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf(),
    filters = listOf(
        string("Invalid flag: "),
    ),
)
