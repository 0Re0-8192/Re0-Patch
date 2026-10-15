package app.re0.patches.gboard.japanese

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import app.re0.patches.gboard.shared.extensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lapp/re0/extension/gboard/JapaneseDigitsPatch;"

/**
 * Key of the switch. Must be the same as JapaneseDigitsPatch.PREFERENCE_KEY.
 */
private const val PREFERENCE_KEY = "re0_half_width_digits"

/**
 * The preference screen with the "Keys" category: number row, emoji key, comma key, period key.
 */
private const val KEYS_PREFERENCE_SCREEN = "res/0CM.xml"

private val addSettingsSwitchPatch = resourcePatch(
    description = "Adds the half width digits switch to the Gboard settings.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        document(KEYS_PREFERENCE_SCREEN).use { document ->
            // The first category is "Keys".
            val keysCategory = document.getElementsByTagName("androidx.preference.PreferenceCategory").item(0)
                as? Element ?: throw PatchException("Could not find the preference category in $KEYS_PREFERENCE_SCREEN")

            val switchCount = keysCategory.getElementsByTagName("SwitchPreferenceCompat").length
            if (switchCount < 3) {
                throw PatchException("Unexpected preference screen, the Keys category has $switchCount switches")
            }

            val preference = document.createElement("SwitchPreferenceCompat").apply {
                setAttribute("android:persistent", "true")
                setAttribute("android:key", PREFERENCE_KEY)
                setAttribute("android:title", "数字を常に半角で入力")
                setAttribute(
                    "android:summary",
                    "日本語入力中に数字キーで打った数字を、最初から半角にします。変換候補には全角も出ます。" +
                        "数字のあとの「。」「、」は、そのまま「。」「、」にします。" +
                        "切り替えたあとは、入力欄を開き直すと反映されます",
                )
                setAttribute("android:defaultValue", "true")
            }

            keysCategory.appendChild(preference)
        }
    }
}

@Suppress("unused")
val halfWidthDigitsPatch = bytecodePatch(
    name = "Half-width digits in Japanese input",
    description = "Digits typed in Japanese input are half width from the start, instead of full width. " +
        "A Japanese period or comma typed after a digit stays as it is, and is not changed to " +
        "the full width \".\" or \",\". " +
        "Full width digits stay in the conversion candidates. " +
        "Adds a switch to Gboard settings > Keys.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(extensionPatch, addSettingsSwitchPatch)

    execute {
        val method = EvalCommandFingerprint.method
        val callIndex = EvalCommandFingerprint.instructionMatches.first().index

        val call = method.getInstruction(callIndex) as? FiveRegisterInstruction
            ?: throw PatchException("Unexpected MozcJni.evalCommand call")
        val commandRegister = call.registerC
        // The non range form of invoke can only use the registers up to v15.
        if (commandRegister > 15) {
            throw PatchException("The command register v$commandRegister is out of range")
        }

        val resultInstruction = method.getInstruction(callIndex + 1)
        if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
            throw PatchException("Unexpected instruction after MozcJni.evalCommand: ${resultInstruction.opcode}")
        }
        val outputRegister = (resultInstruction as OneRegisterInstruction).registerA
        if (outputRegister > 15) {
            throw PatchException("The output register v$outputRegister is out of range")
        }

        // Pass the serialized output of the engine through the extension, right after it is received.
        // Added first, because the other injection shifts the instruction index.
        method.addInstructions(
            callIndex + 2,
            """
                invoke-static { v$outputRegister }, $EXTENSION_CLASS->adjustOutput([B)[B
                move-result-object v$outputRegister
            """,
        )

        // Pass the serialized command through the extension, right before it is sent to the engine.
        method.addInstructions(
            callIndex,
            """
                invoke-static { v$commandRegister }, $EXTENSION_CLASS->adjustCommand([B)[B
                move-result-object v$commandRegister
            """,
        )
    }
}
