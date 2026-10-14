package app.re0.patches.gboard.branding

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Element

@Suppress("unused")
val changeAppNamePatch = resourcePatch(
    name = "Change app name",
    description = "Changes the app name, which is also the keyboard name in the system keyboard list. " +
        "Useful to tell a cloned Gboard apart from the original.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    val appName by stringOption(
        key = "appName",
        default = "Gboard Re0",
        title = "App name",
        description = "The name of the app and the keyboard.",
        required = true,
    )

    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            // The input method service has no label of its own and uses the app label.
            application.setAttribute("android:label", appName!!)
        }
    }
}
