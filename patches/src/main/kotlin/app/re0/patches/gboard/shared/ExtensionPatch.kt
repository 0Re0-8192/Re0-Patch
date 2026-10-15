package app.re0.patches.gboard.shared

import app.morphe.patcher.patch.bytecodePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD

/**
 * Adds the extension, the Java code that runs inside Gboard, to the app.
 * Patches that call the extension depend on this patch.
 */
internal val extensionPatch = bytecodePatch(
    description = "Adds the extension code to the app.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    extendWith("extensions/extension.mpe")
}
