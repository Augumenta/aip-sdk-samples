# Useful obfuscated stack traces
-printmapping out.map

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Removes debug and verbose log messages
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
