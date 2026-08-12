import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Подпись релиза берётся из keystore.properties (в .gitignore, в репозиторий не попадает).
// Файла нет — релиз собрался бы неподписанным, поэтому ниже стоит защита.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.chargekg.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chargekg.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.1.3"

        // Локали ограничены тремя: остальные переводы androidx только занимают место.
        resourceConfigurations += listOf("ru", "ky", "en")
        vectorDrawables.useSupportLibrary = false

        // По умолчанию боевой сервер — чтобы отладочная сборка работала на
        // живом телефоне без правок. Для эмулятора против docker compose:
        //   ./gradlew installDebug -PchargekgApi=http://10.0.2.2:8080
        val apiBase = (project.findProperty("chargekgApi") as String?)
            ?: "https://api.chargekg.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")

                // v1 не нужен: minSdk 26 понимает v2. v3 включаем явно — только
                // он позволяет однажды сменить ключ подписи, не заставляя всех
                // сносить приложение и терять настройки.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Размер APK — главное ограничение проекта: обновление человек качает
            // руками, возможно с мобильного интернета. R8 и выброс ресурсов обязательны.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // Имя ассета в релизе на GitHub: ровно его ищет UpdateChecker.
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "ChargeKG-v${variant.versionName}.apk"
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }

    lint {
        // В Google Play не публикуемся: обновления идут через GitHub Releases.
        disable += "ExpiredTargetSdkVersion"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Публикуемый релиз обязан быть подписан: иначе он не поставится поверх
// установленного, и автообновление молча перестанет работать.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }
    if (wantsRelease && !keystorePropsFile.exists() && !project.hasProperty("allowUnsignedRelease")) {
        throw GradleException(
            "keystore.properties не найден — релиз собрался бы НЕПОДПИСАННЫМ. " +
                "Добавьте keystore.properties или передайте -PallowUnsignedRelease."
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    // Cloudflare перед API уже отдаёт brotli, а OkHttp сам его не умеет: с ним
    // полный список приезжает 39 КБ вместо 53. Плата — вес библиотеки в APK,
    // см. замер в README.
    implementation(libs.okhttp.brotli)
    implementation(libs.osmdroid)

    // Намеренно НЕ подключены: Hilt, Room, Retrofit, kotlinx-serialization,
    // DataStore, WorkManager, play-services-*, material-icons-extended,
    // appcompat (язык переключается подменой конфигурации — см. util/Locales.kt).
    // Причины — в плане проекта: каждая из них стоит сотни килобайт при
    // нулевой пользе на шести экранах и одном кэшируемом ответе.

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
