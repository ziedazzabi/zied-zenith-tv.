-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.zenith.iptv.**$$serializer { *; }
-keepclassmembers class com.zenith.iptv.** { *** Companion; }
-keepclasseswithmembers class com.zenith.iptv.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# LibVLC (JNI)
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**
