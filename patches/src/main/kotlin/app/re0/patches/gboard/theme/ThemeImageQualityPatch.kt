package app.re0.patches.gboard.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * The result handler of the image picker of the theme builder (ThemeBuilderActivity.handleImageUri):
 *
 * ```
 * Glide.with(activity).asBitmap().load(uri).apply(new RequestOptions().override(512, 512)).listener(...).submit()
 * ```
 *
 * The image of a new theme is loaded at most 512 by 512 pixels, so it gets blurry on the keyboard.
 */
internal object ThemeImageLoadFingerprint : Fingerprint(
    strings = listOf("handleImageUri"),
    filters = listOf(literal(512, listOf(Opcode.CONST_16))),
)

@Suppress("unused")
val themeImageQualityPatch = bytecodePatch(
    name = "Better theme image quality",
    description = "The image of a new keyboard theme is loaded at most 512 by 512 pixels, which is blurry on the " +
        "keyboard. This raises the limit.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    val maxSide by intOption(
        key = "maxSide",
        default = 2048,
        title = "Longest side in pixels",
        description = "The image of a new theme is loaded at most this many pixels wide and high.",
        required = true,
    ) { it != null && it in 512..8192 }

    execute {
        val match = ThemeImageLoadFingerprint.instructionMatches.single()
        val method = ThemeImageLoadFingerprint.method

        val register = (method.getInstruction(match.index) as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Unexpected instruction for the size of the theme image")
        if (register > 255) {
            throw PatchException("The register v$register is out of range")
        }

        // const/16 holds a signed 16 bit value.
        method.replaceInstruction(match.index, "const/16 v$register, $maxSide")
    }
}
