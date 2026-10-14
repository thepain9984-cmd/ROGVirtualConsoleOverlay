# Keep model classes for Gson serialization
-keep class com.custom.rogcontrols.model.** { *; }
-keepclassmembers class com.custom.rogcontrols.model.** { *; }

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
