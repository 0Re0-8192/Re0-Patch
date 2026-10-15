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
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lapp/re0/extension/gboard/AnimatedBackgroundPatch;"

/**
 * The view of the keys. Its layout is the keyboard header and body, each with the background of the theme.
 */
private const val KEYBOARD_VIEW_CLASS = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;"

/**
 * The view behind the keys that has the background of the theme, such as the image (the tag ".keyboard-background").
 */
private const val THEME_BACKGROUND_VIEW_CLASS = "Lcom/google/android/libraries/inputmethod/widgets/ShrinkableFrameView;"

/**
 * Keys of the switches. Must be the same as in the extension.
 */
private const val COLOR_PREFERENCE_KEY = "re0_bg_color_cycle"
private const val PAN_PREFERENCE_KEY = "re0_bg_pan"

/**
 * The preference screen that has the "Keys" category. The switches get a category of their own, at the end.
 */
private const val PREFERENCE_SCREEN = "res/0CM.xml"

private val addSettingsSwitchesPatch = resourcePatch(
    description = "Adds the switches of the keyboard background animation to the Gboard settings.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        document(PREFERENCE_SCREEN).use { document ->
            val screen = document.documentElement
            val category = document.createElement("androidx.preference.PreferenceCategory").apply {
                setAttribute("android:title", "背景のアニメーション")
            }

            fun addSwitch(key: String, title: String, summary: String) {
                category.appendChild(
                    document.createElement("SwitchPreferenceCompat").apply {
                        setAttribute("android:persistent", "true")
                        setAttribute("android:key", key)
                        setAttribute("android:title", title)
                        setAttribute("android:summary", summary)
                        setAttribute("android:defaultValue", "true")
                    },
                )
            }

            addSwitch(
                COLOR_PREFERENCE_KEY,
                "背景の色をゆっくり変える",
                "キーボードの背景に、色相がゆっくり回る色を重ねます。1周は1分です。",
            )
            addSwitch(
                PAN_PREFERENCE_KEY,
                "背景の画像をゆっくり動かす",
                "テーマの背景が画像のとき、少し拡大して、ゆっくり動かします。",
            )

            (screen as Element).appendChild(category)
        }
    }
}

@Suppress("unused")
val animatedBackgroundPatch = bytecodePatch(
    name = "Animated keyboard background",
    description = "Moves the keyboard background slowly: a color that changes its hue, and the image of the theme " +
        "that pans. Adds two switches to Gboard settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(extensionPatch, addSettingsSwitchesPatch)

    execute {
        hookView(KEYBOARD_VIEW_CLASS, "drawColor")
        hookView(THEME_BACKGROUND_VIEW_CLASS, "drawPan")
    }
}

/**
 * Starts the redraw loop when the view is attached to the window, and draws an effect after the background of the
 * view, before its children.
 *
 * @param drawMethod The method of the extension to call from onDraw.
 */
private fun BytecodePatchContext.hookView(viewClass: String, drawMethod: String) {
    val view = mutableClassDefBy(viewClass)
    val superClass = view.superclass ?: throw PatchException("$viewClass has no superclass")

    val attached = view.methods.singleOrNull { it.name == "onAttachedToWindow" && it.parameterTypes.isEmpty() }
    if (attached != null) {
        attached.addInstructions(0, "invoke-static { p0 }, $EXTENSION_CLASS->onAttached(Landroid/view/View;)V")
    } else {
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

    if (view.methods.any { it.name == "onDraw" }) {
        throw PatchException("$viewClass already has onDraw")
    }
    view.methods.add(
        ImmutableMethod(
            view.type,
            "onDraw",
            listOf(ImmutableMethodParameter("Landroid/graphics/Canvas;", null, null)),
            "V",
            AccessFlags.PROTECTED.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable().apply {
            addInstructions(
                0,
                """
                    invoke-super { p0, p1 }, $superClass->onDraw(Landroid/graphics/Canvas;)V
                    invoke-static { p0, p1 }, $EXTENSION_CLASS->$drawMethod(Landroid/view/View;Landroid/graphics/Canvas;)V
                    return-void
                """,
            )
        },
    )
}
