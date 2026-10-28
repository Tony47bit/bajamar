# Reglas de R8 para la versión de lanzamiento (minificación desactivada por ahora).
# Si activas isMinifyEnabled, conserva los modelos de kotlinx.serialization:
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pescasub.app.data.remote.** { *** Companion; }
-keepclasseswithmembers class com.pescasub.app.data.remote.** { kotlinx.serialization.KSerializer serializer(...); }
