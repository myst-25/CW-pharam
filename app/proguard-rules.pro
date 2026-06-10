# Keep Xposed Module entry point
-keep class com.cwpdf.saver.MainHook { *; }

# Keep libxposed API classes so they are not stripped
-keep class io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.api.**
