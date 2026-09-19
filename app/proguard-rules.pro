# Keep widget plugin contract so external plugin APKs can implement it.
-keep interface com.livingroomhq.core.widget.WidgetPlugin { *; }
-keep class com.livingroomhq.core.widget.** { *; }

# Whisper JNI
-keep class com.livingroomhq.translate.whisper.WhisperLib { *; }
-keep class com.livingroomhq.translate.whisper.WhisperLib$Companion { *; }

