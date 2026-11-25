package app.re0.patches.gboard.backup

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.re0.patches.gboard.shared.Constants.COMPATIBILITY_GBOARD
import app.re0.patches.gboard.shared.extensionPatch
import org.w3c.dom.Element

private const val BACKUP_ACTIVITY = "app.re0.extension.gboard.BackupActivity"
private const val APP_PACKAGE = "com.google.android.inputmethod.latin"

/** The preference screen that the background picker also adds its entry to. */
private const val PREFERENCE_SCREEN = "res/0CM.xml"

private val backupResourcesPatch = resourcePatch(
    description = "Adds the backup activity to the manifest, and its entry to the Gboard settings.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            application.appendChild(
                document.createElement("activity").apply {
                    setAttribute("android:name", BACKUP_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:label", "設定の保存")
                    setAttribute("android:theme", "@android:style/Theme.DeviceDefault.DayNight")
                },
            )
        }

        document(PREFERENCE_SCREEN).use { document ->
            val category = document.createElement("androidx.preference.PreferenceCategory").apply {
                setAttribute("android:title", "設定の保存")
            }
            category.appendChild(
                document.createElement("Preference").apply {
                    setAttribute("android:title", "書き出し・読み込み")
                    appendChild(
                        document.createElement("intent").apply {
                            setAttribute("android:targetPackage", APP_PACKAGE)
                            setAttribute("android:targetClass", BACKUP_ACTIVITY)
                        },
                    )
                },
            )
            document.documentElement.appendChild(category)
        }
    }
}

@Suppress("unused")
val settingsBackupPatch = bytecodePatch(
    name = "Settings backup",
    description = "Saves the Gboard settings, the theme, the word list, the clipboard, the learning of Mozc and " +
        "karukan, and the Re0 background to a file, and reads them back, so they survive a reinstall. " +
        "Adds an entry to Gboard settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(extensionPatch, backupResourcesPatch)

    execute {
        // The activity is in the extension, and the entry is added by the resource patch.
    }
}
