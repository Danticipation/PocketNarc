# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep sensor-related classes
-keep class android.hardware.Sensor { *; }
-keep class android.hardware.SensorEvent { *; }

# Keep Compose
-dontwarn androidx.compose.**
