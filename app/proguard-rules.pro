# osmdroid читает свои настройки рефлексией по именам полей Configuration,
# без этого правила карта падает при первом обращении к тайлам.
-keep class org.osmdroid.config.** { *; }
-keep class org.osmdroid.tileprovider.** { *; }

# osmdroid тянет необязательные зависимости, которых в APK нет.
-dontwarn org.osmdroid.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp: платформенные классы под Android отсутствуют, предупреждения шумят.
-dontwarn okhttp3.internal.platform.**
-dontwarn kotlin.Unit
