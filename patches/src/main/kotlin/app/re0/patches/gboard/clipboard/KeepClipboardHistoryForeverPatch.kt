package app.re0.patches.gboard.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD

/**
 * 100 years. Not Long.MAX_VALUE, because Gboard subtracts it from the current time.
 */
private const val TIME_LIMIT_MILLISECONDS = 100L * 365 * 24 * 60 * 60 * 1000

@Suppress("unused")
val keepClipboardHistoryForeverPatch = bytecodePatch(
    name = "Keep clipboard history forever",
    description = "Unpinned clips are no longer deleted after 1 hour, and stay until you delete them. " +
        "Gboard still keeps at most about 100 unpinned clips. " +
        "Overrides the setting that \"Extend clipboard history\" adds.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        UnpinnedItemTimeLimitFingerprint.method.addInstructions(
            0,
            """
                const-wide v0, ${TIME_LIMIT_MILLISECONDS}L
                return-wide v0
            """,
        )
    }
}
