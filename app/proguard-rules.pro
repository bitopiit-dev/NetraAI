# MediaPipe / TFLite
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# AutoValue annotations used by MediaPipe
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**

# libVLC is driven from native code through JNI: R8 must not rename or strip it
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**

# MediaPipe tasks: native code reads protobuf / option classes by name
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# App model classes (Room entities, enums stored by name)
-keep class com.factory.netra.data.db.** { *; }
-keepclassmembers enum * { *; }
