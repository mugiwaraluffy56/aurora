# Keep Android entry points and Media3 service classes reachable after R8.
-keep class com.aurora.cinema.AuroraApplication { *; }
-keep class com.aurora.cinema.MainActivity { *; }
-keep class com.aurora.cinema.playback.AuroraPlaybackService { *; }

# Room reflects over generated schema metadata during migrations.
-keep class com.aurora.cinema.library.db.** { *; }

# Native bridge methods are resolved from Kotlin and JNI.
-keep class com.aurora.cinema.core.nativebridge.** { *; }
