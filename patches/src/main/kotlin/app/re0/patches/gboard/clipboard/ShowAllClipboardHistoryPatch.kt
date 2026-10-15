package app.re0.patches.gboard.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.re0.patches.gboard.flags.addFlagOverrides
import app.re0.patches.gboard.flags.flagOverridesHookPatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction

@Suppress("unused")
val showAllClipboardHistoryPatch = bytecodePatch(
    name = "Show all clipboard history",
    description = "The clipboard only shows the clips of the last 5 copies, and hides older ones for good. " +
        "This shows all saved clips, up to Gboard's limit of about 100 unpinned clips.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    // To force the query that this patch changes.
    dependsOn(flagOverridesHookPatch)

    execute {
        // The older query code has the same limit. Use the newer one, which Gboard uses by default.
        addFlagOverrides("enable_clipboard_query_refactoring=true")

        ClipboardItemsQueryFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val (maxMatch, _, limitMatch) = fingerprint.instructionMatches

            // Ignore the saved threshold time, below which clips are hidden. It is the timestamp of the
            // oldest clip shown last time, so clips past the limit would stay hidden after removing it.
            // Math.max(a, threshold) becomes Math.max(a, a).
            val timeLimitRegister = when (val call = method.getInstruction(maxMatch.index)) {
                is FiveRegisterInstruction -> call.registerC
                is RegisterRangeInstruction -> call.startRegister
                else -> throw PatchException("Unexpected Math.max call: $call")
            }
            // A non range invoke can only address registers up to v15.
            if (timeLimitRegister + 1 > 15) {
                throw PatchException("Math.max argument register out of range: v$timeLimitRegister")
            }
            val a = "v$timeLimitRegister, v${timeLimitRegister + 1}"
            method.replaceInstruction(maxMatch.index, "invoke-static { $a, $a }, Ljava/lang/Math;->max(JJ)J")

            // Remove "if (copies >= 5) break;". A label points to this instruction, so replace it.
            method.replaceInstruction(limitMatch.index, "nop")
        }
    }
}
