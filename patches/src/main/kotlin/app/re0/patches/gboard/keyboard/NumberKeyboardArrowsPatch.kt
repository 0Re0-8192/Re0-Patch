package app.re0.patches.gboard.keyboard

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import app.re0.patches.gboard.shared.keyboardCachePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val SOFT_KEY_VIEW = "com.google.android.libraries.inputmethod.widgets.SoftKeyView"

/**
 * Layouts of the number and phone keyboards. They have 4 rows of 4 keys, and the last key is
 * key_pos_ime_action. The first is for phones, the other for large tablets in the normal and split modes.
 */
internal val NUMBER_LAYOUT_FILES = listOf("res/IwS.xml", "res/v73.xml", "res/tEQ.xml", "res/YuW.xml")
internal val NUMBER_LAYOUT_REFERENCES = setOf(
    "@layout/layout_0x7f0e0594", "@layout/layout_0x7f0e0595",
    // The same keyboard with 3 columns (4 rows of 3 keys), used for the number password input.
    "@layout/layout_0x7f0e0596", "@layout/layout_0x7f0e0597",
)

/**
 * Keyboard definitions that use the layouts. LatinNumberKeyboard for the number inputs (two of them),
 * LatinDialKeyboard for the phone number input, and LatinNumberKeyboard for the number password input.
 */
internal val KEYBOARD_DEFINITION_FILES = listOf("res/elQ.xml", "res/gJw.xml", "res/4ht.xml", "res/AVH.xml")

/**
 * Bottom rows of the letter keyboards for phones (and the password keyboards, which use the same ones). They have
 * the space key, but no arrow keys: only the large screens have the rows with them. Each file is a row, with the
 * keys in weights of the same sum, and the space key shares its width with the arrow keys.
 */
internal val PHONE_ROW_LAYOUT_FILES = listOf(
    "res/ivv.xml", "res/aoI.xml", "res/XhS.xml", "res/tqw.xml", "res/f6i.xml", "res/AGl.xml", "res/7Na.xml",
)
private const val SPACE_VIEW_ID = "@id/key_pos_space"

/** Each arrow key takes this part of the width of the space key. */
private const val ROW_ARROW_SHARE_PERCENT = 25

/**
 * The definition of the keys that already exist in the other keyboards: the left and right arrow keys.
 * Sliding up and down on them are the up and down arrows.
 */
internal const val ARROW_KEYS_DEFINITION = "@xml/xml_0x7f17104a"
private const val LEFT_ARROW_KEY_ID = "@id/id_0x7f0b1a19"
private const val RIGHT_ARROW_KEY_ID = "@id/id_0x7f0b2336"
internal const val LEFT_ARROW_VIEW_ID = "@id/key_pos_left_arrow"
internal const val RIGHT_ARROW_VIEW_ID = "@id/key_pos_right_arrow"

/**
 * The style of the bottom row keys of the other keyboards, which have the arrow keys with a key background.
 * The style of the keys next to the digits gave arrow keys without a background.
 */
private const val ARROW_KEY_STYLE = "@style/style_0x7f1506e3"

internal const val ROW_WEIGHT = 250
private const val ROWS_BEFORE = 4

@Suppress("unused")
val numberKeyboardArrowsPatch = resourcePatch(
    name = "Arrow keys in number keyboards",
    description = "Adds a row with the left and right arrow keys to the number and phone keyboards (also the " +
        "number password one), and the two arrow keys next to the space key of the letter keyboards for phones " +
        "(also the password ones). Sliding up or down on an arrow key moves up or down.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(keyboardCachePatch)

    execute {
        NUMBER_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addArrowRow(document, path) }
        }

        PHONE_ROW_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addArrowsAfterSpace(document, path) }
        }

        KEYBOARD_DEFINITION_FILES.forEach { path ->
            document(path).use { document -> addArrowKeys(document, path) }
        }
    }
}

/**
 * Puts the two arrow keys right of the space key, and takes their width from the space key. They are gone until
 * the keyboard definition maps the arrow keys to them, as the keyboards that do not have the definition would
 * show an empty key otherwise.
 */
private fun addArrowsAfterSpace(document: Document, path: String) {
    val spaces = document.getElementsByTagName("*").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }.filter { it.getAttribute("android:id") == SPACE_VIEW_ID }
    val space = spaces.singleOrNull() ?: throw PatchException("Expected one space key in $path, found ${spaces.size}")

    val spaceWeight = space.getAttribute("android:layout_weight").toFloatOrNull()
        ?: throw PatchException("The space key has no weight in $path")
    val arrowWeight = (spaceWeight * ROW_ARROW_SHARE_PERCENT / 100).toInt().toFloat()
    if (arrowWeight <= 0 || arrowWeight * 2 >= spaceWeight) {
        throw PatchException("Unexpected weight of the arrow keys in $path: $arrowWeight of $spaceWeight")
    }

    fun format(weight: Float) = weight.toInt().toString()
    space.setAttribute("android:layout_weight", format(spaceWeight - arrowWeight * 2))

    val next = space.nextSibling
    listOf(LEFT_ARROW_VIEW_ID, RIGHT_ARROW_VIEW_ID).forEach { viewId ->
        val key = document.createElement(SOFT_KEY_VIEW).apply {
            setAttribute("android:id", viewId)
            setAttribute("android:layout_weight", format(arrowWeight))
            setAttribute("android:visibility", "gone")
            setAttribute("style", ARROW_KEY_STYLE)
        }
        space.parentNode.insertBefore(key, next)
    }
}

/**
 * Adds a row under the last one, with the left arrow key in the left half, and the right one in the right half.
 */
private fun addArrowRow(document: Document, path: String) {
    val lastKeys = document.getElementsByTagName(SOFT_KEY_VIEW).let { keys ->
        (0 until keys.length).map { keys.item(it) as Element }
    }.filter { it.getAttribute("android:id") == "@id/key_pos_ime_action" }

    val lastKey = lastKeys.singleOrNull()
        ?: throw PatchException("Expected one last key in $path, found ${lastKeys.size}")
    val lastRow = lastKey.parentNode as Element
    val rows = lastRow.parentNode as Element

    val rowCount = (0 until rows.childNodes.length).count { rows.childNodes.item(it) is Element }
    if (rowCount != ROWS_BEFORE) {
        throw PatchException("Expected $ROWS_BEFORE rows in $path, found $rowCount")
    }

    val arrowRow = lastRow.cloneNode(false) as Element
    listOf(LEFT_ARROW_VIEW_ID, RIGHT_ARROW_VIEW_ID).forEach { viewId ->
        val key = lastKey.cloneNode(true) as Element
        key.setAttribute("android:id", viewId)
        key.setAttribute("style", ARROW_KEY_STYLE)
        // Two columns wide.
        key.setAttribute("android:layout_weight", (ROW_WEIGHT * 2).toString())
        arrowRow.appendChild(key)
    }
    rows.appendChild(arrowRow)

    // The rows share the height by their weights.
    rows.setAttribute("android:weightSum", ((ROWS_BEFORE + 1) * ROW_WEIGHT).toString())
}

/**
 * Loads the definition of the arrow keys, and puts them into the two new views.
 */
private fun addArrowKeys(document: Document, path: String) {
    val bodies = document.getElementsByTagName("view").let { views ->
        (0 until views.length).map { views.item(it) as Element }
    }.filter { it.getAttribute("type") == "body" && it.getAttribute("layout") in NUMBER_LAYOUT_REFERENCES }

    if (bodies.isEmpty()) {
        throw PatchException("Could not find a keyboard body with a number layout in $path")
    }

    bodies.forEach { body ->
        body.appendChild(
            document.createElement("softkeys").apply { setAttribute("href", ARROW_KEYS_DEFINITION) },
        )

        val keyMapping = document.createElement("key_mapping")
        listOf(
            LEFT_ARROW_KEY_ID to LEFT_ARROW_VIEW_ID,
            RIGHT_ARROW_KEY_ID to RIGHT_ARROW_VIEW_ID,
        ).forEach { (keyId, viewId) ->
            keyMapping.appendChild(
                document.createElement("mapping").apply {
                    setAttribute("key_id", keyId)
                    setAttribute("view_id", viewId)
                },
            )
        }
        body.appendChild(keyMapping)
    }
}
