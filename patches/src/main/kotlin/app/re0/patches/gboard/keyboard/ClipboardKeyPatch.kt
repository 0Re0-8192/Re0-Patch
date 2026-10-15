package app.re0.patches.gboard.keyboard

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import app.re0.patches.gboard.shared.keyboardCachePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val KEY_VIEW_ID_NAME = "re0_key_clipboard"
private const val KEY_ID_NAME = "re0_softkey_clipboard"
private const val KEY_VIEW_ID = "@id/$KEY_VIEW_ID_NAME"
private const val KEY_ID = "@id/$KEY_ID_NAME"

private const val QUESTION_MARK_STRING_NAME = "re0_question_mark"
private const val QUESTION_MARK_STRING = "@string/$QUESTION_MARK_STRING_NAME"

private const val SPACE_VIEW_ID = "@id/key_pos_space"
private const val GLOBE_VIEW_ID = "@id/key_pos_qwerty_globe"
private const val COMMA_VIEW_ID = "@id/A02"
private const val LEFT_ARROW_KEY_ID = "@id/id_0x7f0b1a19"

/** The clipboard icon of the toolbar, and its title. */
private const val CLIPBOARD_ICON = "@drawable/drawable_0x7f080557"
private const val CLIPBOARD_TITLE = "@string/string_0x7f140643"
private const val DEFAULT_ICON_LOCATION = "@id/id_0x7f0b03c0"

/** The type of the clipboard keyboard, the same Gboard opens from the clipboard icon of the toolbar. */
private const val CLIPBOARD_KEYBOARD = "clipboard"

/**
 * Layouts with the bottom row of the letter keyboards, which have the space key and the arrow keys.
 * The first group is rows, the second is a column on the right side, with the delete key on top
 * and the enter key at the bottom.
 */
private val ROW_LAYOUT_FILES = listOf(
    "res/7-_.xml", "res/7Ud.xml", "res/Mj0.xml", "res/SEn.xml", "res/Xcf.xml",
    "res/iy4.xml", "res/sdL.xml", "res/wgL.xml", "res/zMO.xml", "res/zRn.xml",
)
private val COLUMN_LAYOUT_FILES = listOf("res/5nF.xml", "res/Iiq.xml", "res/GeB.xml", "res/O1t.xml")

/**
 * Japanese number and symbol keyboard, split in two for large screens: the bottom row is the back key,
 * two symbol keys, the space key, three more keys and the enter key. It has no arrow keys.
 */
private val SPLIT_NUMBER_LAYOUT_FILES = listOf("res/B5K.xml", "res/hK2.xml")

/**
 * Japanese number and symbol keyboard with the 12 keys, for phones and large screens: the left column has the
 * keys for the undo, the left arrow, the symbols and the input mode.
 */
private val TWELVE_KEY_LAYOUT_FILES = listOf("res/3eV.xml", "res/kxO.xml")

private const val SYMBOL_VIEW_ID = "@id/key_pos_switch_to_symbol"
private const val SMILEY_VIEW_ID = "@id/key_pos_switch_to_smiley"
private const val BACK_VIEW_ID = "@id/key_pos_back_to_prime"

/**
 * The clipboard key and each arrow key take this part of the width of the space key. The space key keeps the rest.
 */
private const val SPLIT_CLIPBOARD_SHARE_PERCENT = 30
private const val SPLIT_ARROW_SHARE_PERCENT = 25

/**
 * Files that define the arrow keys, to put the definition of the clipboard key next to them.
 * Each of them is used by the keyboards with the layouts above.
 */
private val KEY_DEFINITION_FILES = listOf("res/L93.xml", "res/_fZ.xml", "res/Xyr.xml")

/**
 * Files that map the arrow keys to their views, to map the clipboard key to its view the same way.
 */
private val KEY_MAPPING_FILES = listOf("res/3OC.xml", "res/VWK.xml")

/**
 * The clipboard key takes at most this part of the width of the space key.
 */
private const val SPACE_SHARE_PERCENT = 40

private const val NUMBER_ARROW_WEIGHT = 350f
private const val NUMBER_CLIPBOARD_WEIGHT = 300f

@Suppress("unused")
val clipboardKeyPatch = resourcePatch(
    name = "Clipboard key",
    description = "Adds a key that opens the clipboard to the keyboards: right of the space key in the letter " +
        "keyboards, and between the arrow keys in the number and phone keyboards.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    // The number and phone keyboards get the clipboard key in the row of the arrow keys.
    dependsOn(keyboardCachePatch, numberKeyboardArrowsPatch)

    execute {
        document("res/values/ids.xml").use { document ->
            val resources = document.documentElement
            listOf(KEY_VIEW_ID_NAME, KEY_ID_NAME).forEach { name ->
                resources.appendChild(
                    document.createElement("item").apply {
                        setAttribute("name", name)
                        setAttribute("type", "id")
                    },
                )
            }
        }

        document("res/values/strings.xml").use { document ->
            document.documentElement.appendChild(
                document.createElement("string").apply {
                    setAttribute("name", QUESTION_MARK_STRING_NAME)
                    textContent = "?"
                },
            )
        }

        ROW_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addKeyNextToSpace(document, path, inColumn = false) }
        }
        COLUMN_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addKeyNextToSpace(document, path, inColumn = true) }
        }
        NUMBER_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addKeyBetweenArrows(document, path) }
        }
        SPLIT_NUMBER_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addKeysAroundSpace(document, path) }
        }
        TWELVE_KEY_LAYOUT_FILES.forEach { path ->
            document(path).use { document -> addKeyToSymbolKeys(document, path) }
        }

        KEY_DEFINITION_FILES.forEach { path ->
            document(path).use { document -> addKeyDefinition(document, path) }
        }
        KEY_MAPPING_FILES.forEach { path ->
            document(path).use { document -> addKeyMapping(document, path) }
        }
        KEYBOARD_DEFINITION_FILES.forEach { path ->
            document(path).use { document -> addKeyMappingToNumberBodies(document, path) }
        }
    }
}

private fun Element.childElements() =
    (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

private fun Document.elements(tag: String) =
    getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

/** The space key of some layouts is a SplittableSoftKeyView, so any element with the id counts. */
private fun Document.keyViews(viewId: String) =
    elements("*").filter { it.getAttribute("android:id") == viewId }

private fun Node.previousElement(): Element? {
    var node = previousSibling
    while (node != null && node !is Element) node = node.previousSibling
    return node as Element?
}

private fun Node.nextElement(): Element? {
    var node = nextSibling
    while (node != null && node !is Element) node = node.nextSibling
    return node as Element?
}

/** The weight of the element, which is written as a float such as 424.0 or 424. */
private fun Element.weight() = getAttribute("android:layout_weight").toFloatOrNull()

private fun formatWeight(weight: Float) = if (weight == weight.toInt().toFloat()) weight.toInt().toString() else weight.toString()

/** The weight of the element for each key in it. */
private fun Element.weightPerKey(): Float {
    val weight = weight() ?: return Float.MAX_VALUE
    val keys = if (tagName == SOFT_KEY_VIEW) 1 else getElementsByTagName(SOFT_KEY_VIEW).length
    return weight / maxOf(keys, 1)
}

/**
 * Gone until the keyboard definition maps a key to the view. A keyboard that does not map the clipboard key
 * shows nothing in its place, instead of an empty key.
 */
private fun newKeyView(template: Element, weight: Float) = (template.cloneNode(false) as Element).apply {
    setAttribute("android:id", KEY_VIEW_ID)
    setAttribute("android:layout_weight", formatWeight(weight))
    setAttribute("android:visibility", "gone")
}

/**
 * The key left of the space key in the letter keyboards is the language switch key (the globe). The same key
 * turns into a "?" key while typing Japanese. This replaces it with the clipboard key, which takes the same
 * space, so the row has no extra key.
 */
private fun replaceGlobeKey(globe: Element, leftArrow: Element) {
    globe.setAttribute("android:id", KEY_VIEW_ID)
    globe.setAttribute("style", leftArrow.getAttribute("style"))
    globe.setAttribute("android:visibility", "gone")
}

/**
 * Puts the clipboard key right before the space key, or before the comma key that is before it (after the space
 * key in a column), and takes its width from the space
 * key. Where the layout has the globe key, the clipboard key takes its place instead, which is left of the
 * space key too.
 */
private fun addKeyNextToSpace(document: Document, path: String, inColumn: Boolean) {
    val space = document.keyViews(SPACE_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one space key in $path")
    val leftArrow = document.keyViews(LEFT_ARROW_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one left arrow key in $path")

    val globe = document.keyViews(GLOBE_VIEW_ID).singleOrNull()
    if (globe != null && !inColumn) {
        replaceGlobeKey(globe, leftArrow)
        return
    }

    val spaceWeight = space.weight()
        ?: throw PatchException("The space key has no weight in $path")

    // As wide as the key next to the space key, but not much of the space key.
    val neighbor = if (inColumn) space.nextElement() else space.previousElement() ?: space.nextElement()
    // Where the key before the space key is the comma key, the clipboard key goes before the comma key.
    val anchor = if (!inColumn && neighbor?.getAttribute("android:id") == COMMA_VIEW_ID) neighbor else space
    val weight = minOf(neighbor?.weightPerKey() ?: Float.MAX_VALUE, spaceWeight * SPACE_SHARE_PERCENT / 100)
    if (weight <= 0 || weight >= spaceWeight) {
        throw PatchException("Unexpected weight of the clipboard key in $path: $weight of $spaceWeight")
    }

    // The arrow keys have the style of the keys of a row, but a column needs the style of its own keys.
    val key = newKeyView(if (inColumn) space else leftArrow, weight)
    space.setAttribute("android:layout_weight", formatWeight(spaceWeight - weight))
    space.parentNode.insertBefore(key, if (inColumn) space.nextSibling else anchor)
}

/**
 * Split number keyboard: the clipboard key goes left of the space key, and the arrow keys right of it. The three
 * keys take their width from the space key.
 */
private fun addKeysAroundSpace(document: Document, path: String) {
    val space = document.keyViews(SPACE_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one space key in $path")
    val back = document.keyViews(BACK_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one back key in $path")
    val spaceWeight = space.weight() ?: throw PatchException("The space key has no weight in $path")

    val clipboardWeight = (spaceWeight * SPLIT_CLIPBOARD_SHARE_PERCENT / 100).toInt().toFloat()
    val arrowWeight = (spaceWeight * SPLIT_ARROW_SHARE_PERCENT / 100).toInt().toFloat()
    // The keys of the other bottom rows have the same style as the back key, with a key background.
    fun key(viewId: String, weight: Float) = newKeyView(back, weight).apply { setAttribute("android:id", viewId) }

    space.setAttribute("android:layout_weight", formatWeight(spaceWeight - clipboardWeight - arrowWeight * 2))
    val parent = space.parentNode
    parent.insertBefore(key(KEY_VIEW_ID, clipboardWeight), space)
    val next = space.nextSibling
    parent.insertBefore(key(LEFT_ARROW_VIEW_ID, arrowWeight), next)
    parent.insertBefore(key(RIGHT_ARROW_VIEW_ID, arrowWeight), next)
}

/**
 * 12-key keyboard: the symbol key shares its place with the smiley key, which is hidden unless the keyboard asks
 * for it. The clipboard key is the third key of that place, so the column keeps its number of keys.
 */
private fun addKeyToSymbolKeys(document: Document, path: String) {
    val smiley = document.keyViews(SMILEY_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one smiley key in $path")
    val symbol = document.keyViews(SYMBOL_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one symbol key in $path")
    if (symbol.parentNode != smiley.parentNode) {
        throw PatchException("The symbol key and the smiley key are in different places in $path")
    }

    val weight = smiley.weight() ?: throw PatchException("The smiley key has no weight in $path")
    smiley.parentNode.insertBefore(newKeyView(smiley, weight), smiley.nextSibling)
}

/**
 * Number keyboards: the row of the arrow keys gets the clipboard key between them.
 */
private fun addKeyBetweenArrows(document: Document, path: String) {
    val left = document.keyViews(LEFT_ARROW_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one left arrow key in $path")
    val right = document.keyViews(RIGHT_ARROW_VIEW_ID).singleOrNull()
        ?: throw PatchException("Expected one right arrow key in $path")

    left.setAttribute("android:layout_weight", formatWeight(NUMBER_ARROW_WEIGHT))
    right.setAttribute("android:layout_weight", formatWeight(NUMBER_ARROW_WEIGHT))
    left.parentNode.insertBefore(newKeyView(left, NUMBER_CLIPBOARD_WEIGHT), right)
}

/**
 * Defines the clipboard key next to each definition of the left arrow key.
 */
private fun addKeyDefinition(document: Document, path: String) {
    restoreQuestionMarks(document)

    val arrows = document.elements("softkey").filter { it.getAttribute("id") == LEFT_ARROW_KEY_ID }
    if (arrows.isEmpty()) {
        throw PatchException("Could not find the left arrow key definition in $path")
    }

    arrows.forEach { arrow ->
        val iconLocation = arrow.childElements().firstOrNull { it.tagName == "icon" }
            ?.getAttribute("location")?.takeIf { it.isNotEmpty() } ?: DEFAULT_ICON_LOCATION

        val key = document.createElement("softkey").apply {
            setAttribute("id", KEY_ID)
            // The layout of an icon key, the same as the arrow key.
            setAttribute("layout", arrow.getAttribute("layout"))
            setAttribute("content_description", CLIPBOARD_TITLE)
            appendChild(
                document.createElement("action").apply {
                    setAttribute("keycode", "SWITCH_KEYBOARD")
                    setAttribute("data", CLIPBOARD_KEYBOARD)
                    setAttribute("type", "PRESS")
                },
            )
            appendChild(
                document.createElement("icon").apply {
                    setAttribute("location", iconLocation)
                    setAttribute("value", CLIPBOARD_ICON)
                },
            )
        }
        arrow.parentNode.insertBefore(key, arrow.nextSibling)
    }
}

private fun newMapping(document: Document) = document.createElement("mapping").apply {
    setAttribute("key_id", KEY_ID)
    setAttribute("view_id", KEY_VIEW_ID)
}

/**
 * Maps the clipboard key next to each mapping of the left arrow key.
 */
private fun addKeyMapping(document: Document, path: String) {
    val arrows = document.elements("mapping").filter { it.getAttribute("view_id") == LEFT_ARROW_VIEW_ID }
    if (arrows.isEmpty()) {
        throw PatchException("Could not find the left arrow key mapping in $path")
    }

    arrows.forEach { arrow -> arrow.parentNode.insertBefore(newMapping(document), arrow.nextSibling) }
}

/**
 * The keyboard definitions of the number and phone keyboards map the arrow keys themselves.
 */
private fun addKeyMappingToNumberBodies(document: Document, path: String) {
    val bodies = document.elements("view")
        .filter { it.getAttribute("type") == "body" && it.getAttribute("layout") in NUMBER_LAYOUT_REFERENCES }
    if (bodies.isEmpty()) {
        throw PatchException("Could not find a keyboard body with a number layout in $path")
    }

    bodies.forEach { body ->
        body.appendChild(
            document.createElement("key_mapping").apply { appendChild(newMapping(document)) },
        )
    }
}

/**
 * A value that is only "?" is compiled as a reference to a theme attribute without a name, "?attr/", which the
 * app then shows as the text, instead of the question mark. Escaping it does not help, so the value becomes a
 * reference to a string that is "?".
 */
private fun restoreQuestionMarks(document: Document) {
    document.elements("*").forEach { element ->
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if (attribute.nodeValue == "?") attribute.nodeValue = QUESTION_MARK_STRING
        }
    }
}
