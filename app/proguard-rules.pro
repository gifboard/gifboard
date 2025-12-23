# ProGuard rules for GifBoard
# R8 will obfuscate and shrink the app for smaller APK size

#-------------------------------------------
# GifBoard App Classes
#-------------------------------------------

# Keep the InputMethodService - Android system needs to find it by name
-keep class com.gifboard.GifBoardService { *; }

# Keep custom View used in XML layouts - LayoutInflater uses reflection
-keep class com.gifboard.AspectRatioDraweeView { *; }

# GifItem data class - can be obfuscated (no reflection)
# GifAdapter - can be obfuscated
# SearchHistoryAdapter - can be obfuscated  
# SearchHistoryDbHelper - can be obfuscated
# GifImageLoader - can be obfuscated
# GoogleGifFetcher - can be obfuscated

#-------------------------------------------
# Fresco (Image Loading)
#-------------------------------------------

# Fresco uses native code and reflection
-keep class com.facebook.imagepipeline.** { *; }
-keep class com.facebook.drawee.** { *; }
-keep class com.facebook.common.** { *; }
-dontwarn com.facebook.**

# Keep SoLoader for native library loading
-keep class com.facebook.soloader.** { *; }

#-------------------------------------------
# OkHttp (Networking)
#-------------------------------------------

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.** { *; }
-keepnames class okhttp3.** { *; }
-keepnames class okio.** { *; }

#-------------------------------------------
# Kotlin & Coroutines
#-------------------------------------------

-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

#-------------------------------------------
# General Android
#-------------------------------------------

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
