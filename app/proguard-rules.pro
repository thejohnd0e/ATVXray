# Keep rules minimal for a tiny app.
-keep class libXray.** { *; }
-keep class go.** { *; }
-dontwarn libXray.**
-keepattributes *Annotation*
