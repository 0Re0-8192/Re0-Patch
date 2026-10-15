package app.re0.patches.gboard.shared

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD

/**
 * Gboard keeps the parsed keyboard definitions in a cache on disk. The directory of the cache is named after
 * the version code of the app, and the patched app has the same version code as the original, so an update
 * keeps using the definitions parsed before, without the changes of the patches.
 *
 * This is added to the version code, so the cache of a new generation is used. Increase it when a patch changes
 * a keyboard definition again.
 */
private const val KEYBOARD_CACHE_GENERATION = 2

/**
 * Renews the cache of keyboard definitions.
 * Patches that change keyboard layouts or definitions depend on this patch.
 */
internal val keyboardCachePatch = resourcePatch(
    description = "Makes Gboard parse the keyboard definitions again, instead of using the cached ones.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val versionCode = manifest.getAttribute("android:versionCode").toIntOrNull()
                ?: throw PatchException("Could not read the version code")
            manifest.setAttribute("android:versionCode", (versionCode + KEYBOARD_CACHE_GENERATION).toString())
        }
    }
}
