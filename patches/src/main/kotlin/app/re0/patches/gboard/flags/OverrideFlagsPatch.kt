package app.re0.patches.gboard.flags

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD

@Suppress("unused")
val overrideFlagsPatch = bytecodePatch(
    name = "Override flags",
    description = "Advanced: overrides any Gboard feature flag, including server controlled ones. " +
        "Set overrides with the patch option, or without repatching in " +
        "Android/data/<package>/files/flag_overrides.txt.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(flagOverridesHookPatch)

    val flagOverrides by stringOption(
        key = "flagOverrides",
        default = "",
        title = "Flag overrides",
        description = "Flags to override as name=value, separated by ';'. " +
            "Example: enable_extended_clipboard_history=true;config_keyboard_max_height=1200",
    )

    val logFlags by booleanOption(
        key = "logFlags",
        default = false,
        title = "Log flags",
        description = "Log the name and value of each flag Gboard reads to logcat (tag 'GboardFlags'). " +
            "Useful to discover flags to override.",
    )

    execute {
        // Added after the overrides of other patches, so these win.
        addFlagOverrides(flagOverrides.orEmpty())

        if (logFlags == true) {
            enableFlagLogging()
        }
    }
}
