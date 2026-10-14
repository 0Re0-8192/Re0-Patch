# Shrink the extension so unused libraries (such as the Kotlin stdlib AGP adds) are not merged into the app.
# Keep names and code as written, the patches call the extension by name.
-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.re0.extension.** {
  *;
}
