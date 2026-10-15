package app.re0.patches.gboard.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.re0.patches.gboard.background.customBackgroundPatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val HOOK_CLASS = "Lapp/re0/extension/gboard/ThemeBuilderHook;"

/**
 * ThemeBuilderActivity.requestImportImage: asks the photo picker for an image.
 * ```
 * request.a = ImageOnly; // class nu
 * launcher.launch(request);
 * ```
 */
internal object RequestImportImageFingerprint : Fingerprint(
    strings = listOf("requestImportImage"),
)

/**
 * The handler of the result of the picker (ThemeBuilderActivity.handleImageUri): stores the Uri in
 * ThemeBuilderActivity.r, and loads the image with Glide.
 */
internal object HandleImageUriFingerprint : Fingerprint(
    strings = listOf("handleImageUri"),
)

/**
 * UserImageThemeBuilder.save(File): writes the zip of the theme, with the entries "original_cropping",
 * "background" and "thumbnail".
 */
internal object SaveUserThemeFingerprint : Fingerprint(
    strings = listOf("original_cropping", "thumbnail"),
)

@Suppress("unused")
val themeBuilderVideoPatch = bytecodePatch(
    name = "Video and GIF in the theme builder",
    description = "Lets the image picker of the keyboard theme builder take a video or a GIF. The first frame goes " +
        "through the crop and brightness pages as for a photo. When the theme is saved, the video or the GIF " +
        "becomes the background of the keyboard, with the part that was cropped.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(customBackgroundPatch)

    execute {
        // 1. The picker offers images and videos, not only images.
        RequestImportImageFingerprint.method.apply {
            val instructions = implementation!!.instructions
            val index = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.SGET_OBJECT &&
                    ((instruction as ReferenceInstruction).reference as? FieldReference)?.let { field ->
                        field.definingClass == "Lnu;" && field.name == "a"
                    } == true
            }
            if (index < 0) {
                throw PatchException("Could not find the media type of the picker request")
            }
            val register = (instructions.elementAt(index) as OneRegisterInstruction).registerA
            // nt is "image and video", nu is "image only".
            replaceInstruction(index, "sget-object v$register, Lnt;->a:Lnt;")
        }

        // 2. Keep the picked file.
        HandleImageUriFingerprint.method.apply {
            val instructions = implementation!!.instructions
            val index = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IPUT_OBJECT &&
                    ((instruction as ReferenceInstruction).reference as? FieldReference)?.let { field ->
                        field.definingClass.endsWith("/ThemeBuilderActivity;") &&
                            field.name == "r" && field.type == "Landroid/net/Uri;"
                    } == true
            }
            if (index < 0) {
                throw PatchException("Could not find where the picked Uri is stored")
            }
            val put = instructions.elementAt(index) as TwoRegisterInstruction
            val uri = put.registerA
            val activity = put.registerB
            if (uri > 15 || activity > 15) {
                throw PatchException("The registers v$activity and v$uri are out of range")
            }
            addInstructions(index + 1, "invoke-static { v$activity, v$uri }, $HOOK_CLASS->onPicked(Landroid/app/Activity;Landroid/net/Uri;)V")
        }

        // 3. Use it when the theme is saved.
        SaveUserThemeFingerprint.method.addInstructions(
            0,
            "invoke-static { p0 }, $HOOK_CLASS->onSave(Ljava/lang/Object;)V",
        )
    }
}
