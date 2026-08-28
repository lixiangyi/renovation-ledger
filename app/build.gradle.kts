plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.renovation.ledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.renovation.ledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("zh", "en")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        // 打包机局域网 IP，开发面板「电脑局域网」一键填入
        buildConfigField("String", "DEV_LAN_URL", "\"${localDevLanUrl()}\"")
        buildConfigField("String", "WECHAT_APP_ID", "\"${wechatAppId()}\"")
        buildConfigField("String", "LLM_API_KEY", "\"${localProperty("LLM_API_KEY")}\"")
        buildConfigField("String", "LLM_PROVIDER", "\"${localProperty("LLM_PROVIDER", "deepseek")}\"")
        // 官网 Release 暂接云测试；商店正式包再把 release 的 DEFAULT_TEST_CLOUD 改为 false
        buildConfigField("boolean", "DEFAULT_TEST_CLOUD", "true")
        buildConfigField("boolean", "ENABLE_DEBUG_PANEL", "false")
    }

    val releaseStoreFile = localProperty("RELEASE_STORE_FILE")
    if (releaseStoreFile.isNotBlank()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (releaseStoreFile.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "ENABLE_DEBUG_PANEL", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "DEFAULT_TEST_CLOUD", "true")
            buildConfigField("boolean", "ENABLE_DEBUG_PANEL", "false")
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

configurations.configureEach {
    resolutionStrategy {
        // dokitx 声明了已下架的 volley:1.1.1，强制到 Maven Central 可用版本
        force("com.android.volley:volley:1.2.1")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.24")

    // MPAndroidChart（对齐 beike_main_project: com.github.PhilJay:MPAndroidChart）
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // DoraemonKit 开源：https://github.com/didi/DoKit
    // 官方发布到 Maven Central（jcenter 下线后 groupId = io.github.didi.dokit）
    // 文档：https://github.com/didi/DoKit/blob/master/Android/README.md
    debugImplementation("io.github.didi.dokit:dokitx:3.7.11")
    releaseImplementation("io.github.didi.dokit:dokitx-no-op:3.7.11")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

fun wechatAppId(): String = localProperty("WECHAT_APP_ID")

fun localProperty(key: String, default: String = ""): String {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return default
    return f.readLines()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=") && !it.startsWith("#") }
        ?.substringAfter("=")
        ?.trim()
        ?.trim('"')
        .orEmpty()
        .ifBlank { default }
}

fun localDevLanUrl(): String {
    val fallback = "http://10.35.86.169:8080/"
    return try {
        val proc = ProcessBuilder("sh", "-c", "ipconfig getifaddr en0 || ipconfig getifaddr en1")
            .redirectErrorStream(true)
            .start()
        val ip = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        if (ip.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
            "http://$ip:8080/"
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
