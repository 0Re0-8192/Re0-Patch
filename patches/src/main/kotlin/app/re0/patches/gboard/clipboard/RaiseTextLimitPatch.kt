package app.re0.patches.gboard.clipboard

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.re0.patches.gboard.flags.addFlagOverrides
import app.re0.patches.gboard.flags.flagOverridesHookPatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD

private const val DEFAULT_LIMIT = 200_000

@Suppress("unused")
val raiseClipboardTextLimitPatch = bytecodePatch(
    name = "Raise clipboard text limit",
    description = "Gboard cuts the text of a clip to 20,000 characters when it saves it, so only that much " +
        "can be pasted. This raises the limit to 200,000 characters. " +
        "Very long texts may fail to load, as the clips are kept in a database with a row size limit.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(flagOverridesHookPatch)

    val limit by intOption(
        key = "limit",
        default = DEFAULT_LIMIT,
        title = "Character limit",
        description = "The most characters Gboard keeps of a clip. Gboard's own limit is 20000.",
        required = true,
    )

    execute {
        addFlagOverrides("text_clip_item_char_limit=${limit ?: DEFAULT_LIMIT}")
    }
}
