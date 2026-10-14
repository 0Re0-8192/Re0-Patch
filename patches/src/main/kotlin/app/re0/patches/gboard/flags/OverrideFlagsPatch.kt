package app.re0.patches.gboard.flags

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val EXTENSION_CLASS = "Lapp/re0/extension/gboard/FlagOverridePatch;"

private const val ORIGINAL_GETTER_NAME = "patch_getOriginalValue"

@Suppress("unused")
val overrideFlagsPatch = bytecodePatch(
    name = "Override flags",
    description = "Overrides Gboard feature flags, including server controlled ones. " +
        "Set overrides with the patch option, or without repatching in " +
        "Android/data/<package>/files/flag_overrides.txt.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    extendWith("extensions/extension.mpe")

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
        // region Bake the option values into the extension.

        mutableClassDefBy(EXTENSION_CLASS).methods.apply {
            first { it.name == "getPatchOverrides" }.addInstructions(
                0,
                """
                    const-string v0, "${flagOverrides.orEmpty().escapeSmaliString()}"
                    return-object v0
                """,
            )

            first { it.name == "isLogFlagsEnabled" }.addInstructions(
                0,
                """
                    const/4 v0, ${if (logFlags == true) "0x1" else "0x0"}
                    return v0
                """,
            )
        }

        // endregion

        // region Route every flag value read through the extension.

        val getter = FlagValueGetterFingerprint.method
        val flagClass = FlagValueGetterFingerprint.classDef

        val nameField = flagClass.fields.singleOrNull { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == "Ljava/lang/String;"
        } ?: throw PatchException("Could not find the flag name field in ${flagClass.type}")

        val implementation = getter.implementation!!
        // Only p0 (this) is a parameter register, and the hook below needs one local register.
        if (implementation.registerCount < 2) {
            throw PatchException("Flag value getter has no free register")
        }

        // Keep the original getter as a new method, and make the getter call it.
        flagClass.methods.add(
            ImmutableMethod(
                flagClass.type,
                ORIGINAL_GETTER_NAME,
                listOf(),
                getter.returnType,
                AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
                null,
                null,
                MutableMethodImplementation(implementation),
            ).toMutable(),
        )

        getter.addInstructions(
            0,
            """
                invoke-direct { p0 }, ${flagClass.type}->$ORIGINAL_GETTER_NAME()Ljava/lang/Object;
                move-result-object v0
                iget-object p0, p0, ${flagClass.type}->${nameField.name}:${nameField.type}
                invoke-static { p0, v0 }, $EXTENSION_CLASS->getFlagValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p0
                return-object p0
            """,
        )

        // endregion
    }
}

private fun String.escapeSmaliString() = buildString {
    for (char in this@escapeSmaliString) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
