package app.re0.patches.gboard.japanese

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.filePathOption
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import app.re0.patches.gboard.shared.extensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.io.File

private const val EXTENSION_CLASS = "Lapp/re0/extension/gboard/KarukanHook;"

/**
 * Where the native library of the engine is stored in the APK. The extension copies it out of the
 * assets on the first run (the libraries folder is not used: whether it is extracted depends on
 * the manifest of the build of Gboard).
 */
private const val LIBRARY_ASSET = "assets/karukan_jni.so"

/** The same library inside the patch file (patches/src/main/resources). */
private const val BUNDLED_LIBRARY = "app/re0/karukan/karukan_jni.so"

@Suppress("unused")
val karukanLibraryPatch = resourcePatch(
    name = "karukan library",
    description = "Adds the native library of the karukan engine to the app. " +
        "Needed by \"karukan Japanese input\", which enables it.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    // Optional: the library is bundled in the patch file. Setting this uses another build instead.
    val library by filePathOption(
        key = "nativeLibrary",
        title = "karukan library",
        description = "Another libkarukan_jni.so (arm64-v8a) to use instead of the bundled one.",
        required = false,
        allowedExtensions = listOf("so"),
    )

    execute {
        val target = get(LIBRARY_ASSET, copy = false)
        target.parentFile.mkdirs()
        val path = library
        if (!path.isNullOrBlank()) {
            val source = File(path)
            if (!source.isFile) {
                throw PatchException("The karukan library does not exist: $source")
            }
            source.copyTo(target, overwrite = true)
        } else {
            val bundled = object {}.javaClass.classLoader?.getResourceAsStream(BUNDLED_LIBRARY)
                ?: throw PatchException("The karukan library is not bundled in this patch file")
            bundled.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
}

@Suppress("unused")
val karukanPatch = bytecodePatch(
    name = "karukan Japanese input",
    description = "Japanese input by the karukan engine instead of Mozc, as in the Windows version. " +
        "Needs the model files in Android/data/<package>/files/karukan. " +
        "Without them, and for everything karukan cannot handle yet, Mozc is used.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    // The half width digits patch changes the same call, and finds it by the call to Mozc. It must
    // run first.
    dependsOn(extensionPatch, karukanLibraryPatch, halfWidthDigitsPatch)

    execute {
        val method = EvalCommandFingerprint.method
        val instructions = method.implementation?.instructions
            ?: throw PatchException("LocalSessionHandler.evalCommand has no code")

        // Look the call up again: the other patch has added instructions before it.
        val callIndex = instructions.indexOfFirst {
            if (it.opcode != Opcode.INVOKE_STATIC) return@indexOfFirst false
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@indexOfFirst false
            reference.definingClass.endsWith("/MozcJni;") && reference.name == "evalCommand"
        }
        if (callIndex < 0) {
            throw PatchException("Could not find the call to MozcJni.evalCommand")
        }

        val call = method.getInstruction(callIndex) as? FiveRegisterInstruction
            ?: throw PatchException("Unexpected MozcJni.evalCommand call")
        val commandRegister = call.registerC
        if (commandRegister > 15) {
            throw PatchException("The command register v$commandRegister is out of range")
        }

        // The result of the call is moved by the next instruction, so only the callee changes.
        method.replaceInstruction(
            callIndex,
            "invoke-static { v$commandRegister }, $EXTENSION_CLASS->evalCommand([B)[B",
        )

        // The extension reads the text before the cursor from the service, as the context of the conversion.
        InputMethodServiceCreateFingerprint.method.addInstructions(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->setService(Landroid/inputmethodservice/InputMethodService;)V",
        )
    }
}
