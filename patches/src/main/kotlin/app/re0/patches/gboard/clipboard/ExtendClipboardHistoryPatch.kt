package app.re0.patches.gboard.clipboard

import app.morphe.patcher.patch.bytecodePatch
import app.re0.patches.gboard.flags.addFlagOverrides
import app.re0.patches.gboard.flags.flagOverridesHookPatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD

@Suppress("unused")
val extendClipboardHistoryPatch = bytecodePatch(
    name = "Extend clipboard history",
    description = "Unpinned clips are deleted after 1 hour. This adds a setting to keep them for up to " +
        "24 hours, in Gboard settings > Clipboard > Keep clipboard history for.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(flagOverridesHookPatch)

    execute {
        // Unhides the retention slider (1 to 24 hours) and makes Gboard use its value.
        addFlagOverrides("enable_extended_clipboard_history=true")
    }
}
