package app.re0.patches.gboard.flags

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val EXTENSION_CLASS = "Lapp/re0/extension/gboard/FlagOverridePatch;"

private const val ORIGINAL_GETTER_NAME = "patch_getOriginalValue"

/**
 * Overrides baked into the extension, in `name=value` form.
 * Later entries win over earlier ones for the same flag.
 */
private val flagOverrides = mutableListOf<String>()

private var logFlags = false

/**
 * Bakes flag overrides into the patched app.
 * Call from the execute block of a patch that depends on [flagOverridesHookPatch].
 *
 * @param overrides One or more `name=value` overrides, separated by `;` or new lines.
 */
internal fun addFlagOverrides(overrides: String) {
    flagOverrides += overrides
}

/**
 * Logs every flag read to logcat.
 * Call from the execute block of a patch that depends on [flagOverridesHookPatch].
 */
internal fun enableFlagLogging() {
    logFlags = true
}

/**
 * Routes every Gboard flag read through the extension, which can replace the value.
 */
internal val flagOverridesHookPatch = bytecodePatch(
    description = "Routes every Gboard flag read through the extension.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    extendWith("extensions/extension.mpe")

    execute {
        // The patcher can run several times in one process, such as in Morphe Manager.
        // Dependent patches execute after this patch and add their overrides again.
        flagOverrides.clear()
        logFlags = false

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
    }

    // Runs after all patches executed, so it sees the overrides every dependent patch added.
    finalize {
        mutableClassDefBy(EXTENSION_CLASS).methods.apply {
            first { it.name == "getPatchOverrides" }.addInstructions(
                0,
                """
                    const-string v0, "${flagOverrides.joinToString("\n").escapeSmaliString()}"
                    return-object v0
                """,
            )

            first { it.name == "isLogFlagsEnabled" }.addInstructions(
                0,
                """
                    const/4 v0, ${if (logFlags) "0x1" else "0x0"}
                    return v0
                """,
            )
        }
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
