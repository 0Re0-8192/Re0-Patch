package app.re0.patches.gboard.background

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import app.re0.patches.gboard.shared.extensionPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lapp/re0/extension/gboard/BackgroundMedia;"

/**
 * The view behind the keys that has the background of the theme (the tag ".keyboard-background").
 * It fills the keyboard, so the video or the image goes in it.
 */
private const val THEME_BACKGROUND_VIEW_CLASS = "Lcom/google/android/libraries/inputmethod/widgets/ShrinkableFrameView;"

private const val THEME_HOOK_CLASS = "Lapp/re0/extension/gboard/ThemeScreenHook;"

/**
 * The fragment of the theme screen. The method that creates its view returns the FrameLayout with the grid of the
 * themes: onCreateView(LayoutInflater, ViewGroup, Bundle).
 */
private const val THEME_LISTING_FRAGMENT_CLASS =
    "Lcom/google/android/apps/inputmethod/libs/theme/listing/ThemeListingFragment;"

private const val PICKER_ACTIVITY = "app.re0.extension.gboard.BackgroundPickerActivity"
private const val APP_PACKAGE = "com.google.android.inputmethod.latin"

/**
 * The preference screen that has the "Keys" category. The entry gets a category of its own, at the end.
 */
private const val PREFERENCE_SCREEN = "res/0CM.xml"

private val pickerResourcesPatch = resourcePatch(
    description = "Adds the picker activity to the manifest, and its entry to the Gboard settings.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            application.appendChild(
                document.createElement("activity").apply {
                    setAttribute("android:name", PICKER_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:label", "背景の動画・画像")
                    // The picker is made of plain views, so it does not need the theme of Gboard.
                    setAttribute("android:theme", "@android:style/Theme.DeviceDefault")
                    setAttribute(
                        "android:configChanges",
                        "orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density",
                    )
                },
            )
        }

        document(PREFERENCE_SCREEN).use { document ->
            val category = document.createElement("androidx.preference.PreferenceCategory").apply {
                setAttribute("android:title", "背景の動画・画像")
            }
            category.appendChild(
                document.createElement("Preference").apply {
                    setAttribute("android:title", "動画か画像を選ぶ")
                    setAttribute(
                        "android:summary",
                        "キーボードの背景に動画や画像を流します。閉じた画面と開いた画面で、見せる場所を別々に決められます。",
                    )
                    appendChild(
                        document.createElement("intent").apply {
                            setAttribute("android:targetPackage", APP_PACKAGE)
                            setAttribute("android:targetClass", PICKER_ACTIVITY)
                        },
                    )
                },
            )
            document.documentElement.appendChild(category)
        }
    }
}

@Suppress("unused")
val customBackgroundPatch = bytecodePatch(
    name = "Video and image background",
    description = "Plays a video or shows an image (GIF, photo) behind the keys of the keyboard. For each of the " +
        "two screens of a foldable phone, the part that is shown can be set, and the picture is not shrunk " +
        "or compressed like the theme background. Adds a picker to Gboard settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(extensionPatch, pickerResourcesPatch)

    execute {
        addThemeScreenEntry()

        val view = mutableClassDefBy(THEME_BACKGROUND_VIEW_CLASS)

        val attached = view.methods.singleOrNull { it.name == "onAttachedToWindow" && it.parameterTypes.isEmpty() }
        if (attached != null) {
            attached.addInstructions(0, "invoke-static { p0 }, $EXTENSION_CLASS->onAttached(Landroid/view/View;)V")
            return@execute
        }

        val superClass = view.superclass ?: throw PatchException("$THEME_BACKGROUND_VIEW_CLASS has no superclass")
        view.methods.add(
            ImmutableMethod(
                view.type, "onAttachedToWindow", listOf(), "V", AccessFlags.PROTECTED.value, null, null,
                MutableMethodImplementation(1),
            ).toMutable().apply {
                addInstructions(
                    0,
                    """
                        invoke-super { p0 }, $superClass->onAttachedToWindow()V
                        invoke-static { p0 }, $EXTENSION_CLASS->onAttached(Landroid/view/View;)V
                        return-void
                    """,
                )
            },
        )
    }
}

/**
 * Passes the view that the theme screen creates through the extension, which adds the entry of the picker.
 * Every return of a view is hooked, so it does not matter which one is the normal path. The extension ignores null.
 */
private fun BytecodePatchContext.addThemeScreenEntry() {
    val fragment = mutableClassDefBy(THEME_LISTING_FRAGMENT_CLASS)
    val method = fragment.methods.singleOrNull {
        it.returnType == "Landroid/view/View;" && it.parameterTypes.map { type -> type.toString() } == listOf(
            "Landroid/view/LayoutInflater;",
            "Landroid/view/ViewGroup;",
            "Landroid/os/Bundle;",
        )
    } ?: throw PatchException("Could not find the method that creates the view of $THEME_LISTING_FRAGMENT_CLASS")

    val returns = method.implementation!!.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .map { it.index to (it.value as OneRegisterInstruction).registerA }
    if (returns.isEmpty()) {
        throw PatchException("The method that creates the view of $THEME_LISTING_FRAGMENT_CLASS has no return")
    }

    // From the last to the first, so the indexes stay valid.
    for ((index, register) in returns.asReversed()) {
        if (register > 15) {
            throw PatchException("The register v$register is out of range")
        }
        method.addInstructions(index, "invoke-static { v$register }, $THEME_HOOK_CLASS->decorate(Landroid/view/View;)V")
    }
}
