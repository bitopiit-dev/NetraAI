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
-dontwarn autovalue.shaded.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
