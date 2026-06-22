# Keep Room generated model metadata and ML Kit runtime entry points stable under R8.
-keep class com.tao.autobook.data.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
