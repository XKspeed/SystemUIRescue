plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xkspeed.systemuimount"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xkspeed.systemuimount"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // activity 1.13.0 起才装配 navigationevent，否则 Miuix 的返回手势拦截会抛
    // IllegalStateException（No NavigationEventDispatcher was provided），启动即崩。
    // 与 ApkEditorMiuix / miuix-example 保持同一组合。
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigationevent:navigationevent-compose-android:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Miuix —— 小米 HyperOS 风格 UI（0.9.4 正式版，2026-09 发布）
    implementation("top.yukonga.miuix.kmp:miuix-core-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4")
    // 顶栏/底栏的实时模糊（示例 App 的 BlurredBar 依赖它）
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")

    // libsu —— root 命令执行（topjohnwu，Magisk 作者，2.1k star）
    // 用它的常驻 shell，避免每条命令都 fork 一个 su 进程
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}
