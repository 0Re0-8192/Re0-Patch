package app.re0.patches.gboard.keyboard

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val SOFT_KEY_VIEW = "com.google.android.libraries.inputmethod.widgets.SoftKeyView"

/**
 * Layouts of the number and phone keyboards. They have 4 rows of 4 keys, and the last key is
 * key_pos_ime_action. The first is for phones, the other for large tablets in the normal and split modes.
 */
private val NUMBER_LAYOUT_FILES = listOf("res/IwS.xml", "res/v73.xml")
private val NUMBER_LAYOUT_REFERENCES = setOf("@layout/layout_0x7f0e0594", "@layout/layout_0x7f0e0595")

/**
 * Keyboard definitions that use the layouts. LatinNumberKeyboard for the number inputs (two of them),
 * and LatinDialKeyboard for the phone number input.
 */
private val KEYBOARD_DEFINITION_FILES = listOf("res/elQ.xml", "res/gJw.xml", "res/4ht.xml")

/**
 * The definition of the keys that already exist in the other keyboards: the left and right arrow keys.
 * Sliding up and down on them are the up and down arrows.
 */
private const val ARROW_KEYS_DEFINITION = "@xml/xml_0x7f17104a"
private const val LEFT_ARROW_KEY_ID = "@id/id_0x7f0b1a19"
private const val RIGHT_ARROW_KEY_ID = "@id/id_0x7f0b2336"
private const val LEFT_ARROW_VIEW_ID = "@id/key_pos_left_arrow"
private const val RIGHT_ARROW_VIEW_ID = "@id/key_pos_right_arrow"

private const val ROW_WEIGHT = 250
private const val ROWS_BEFORE = 4

@Suppress("unused")
val numberKeyboardArrowsPatch = resourcePatch(
    name = "Arrow keys in number keyboards",
    description = "Adds a row with the left and right arrow keys to the number and phone keyboards, " +
        "which have no way to move the cursor. Sliding up or down on an arrow key moves up or down.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        NUMBER_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addArrowRow(document, path) }
        }

        KEYBOARD_DEFINITION_FILES.forEach { path ->
            document(path).use { document -> addArrowKeys(document, path) }
        }
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
        // Same style as the last key, which is a key of the dark kind next to the digits.
        val key = lastKey.cloneNode(true) as Element
        key.setAttribute("android:id", viewId)
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
