package app.re0.patches.gboard.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_GBOARD = Compatibility(
        name = "Gboard",
        packageName = "com.google.android.inputmethod.latin",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x4285F4,
        targets = listOf(
            // Version used to develop the patches.
            AppTarget(
                version = "18.4.1.985164140-release-arm64-v8a",
            ),
            // The flag classes rarely change, so other versions likely work too.
            AppTarget(
                version = null,
                isExperimental = true,
            ),
        ),
    )
}
