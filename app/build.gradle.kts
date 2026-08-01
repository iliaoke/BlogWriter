import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.blog.writer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.blog.writer"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // 只保留 arm64-v8a 的 native 库（不打包 armeabi-v7a / x86 / x86_64），
        // 现在主流真机基本都是 arm64，这一项能显著减小包体积。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // GitHub OAuth 配置。exchangeUrl 指向你自己的“code -> token”中转服务，
        // 若已有该服务，把地址填进去；APP 内不存放 client_secret。
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"Ov23liU1OoI9QzowrTOY\"")
        buildConfigField("String", "OAUTH_REDIRECT_SCHEME", "\"blog\"")
        buildConfigField("String", "OAUTH_REDIRECT_HOST", "\"oauth\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            // 开启代码混淆/收缩 + 资源收缩，去掉未使用的类和资源，是体积优化里收益最大的一项
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // debug 包不用于分发，这里不做任何体积优化，正常调试即可；
            // 请始终用 assembleRelease / bundleRelease 产出要分发的安装包
            isMinifyEnabled = false
        }
    }

    // 只打 arm64-v8a 这一个 ABI 的独立 APK（不生成 universal 包）。
    // 注意：这里和 defaultConfig.ndk.abiFilters 二选一即可，两个同时设置会冲突，
    // 所以只保留上面 defaultConfig 里的 ndk.abiFilters。

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM + Material3
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // 图标全部换成了 res/drawable 下手写的矢量图（AppIcons.kt 统一引用），
    // 不依赖 material-icons-core / material-icons-extended，省下这部分体积

    implementation("androidx.navigation:navigation-compose:2.9.8")

    // 网络
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Custom Tabs 用于打开 GitHub 授权页
    implementation("androidx.browser:browser:1.10.0")

    // Markdown 渲染
    implementation("com.github.jeziellago:compose-markdown:0.7.2")

    // Base64 编解码走 Android 内置 android.util.Base64，无需额外依赖

    // ui-tooling / ui-tooling-preview 只在调试期用 @Preview 时才需要，
    // 项目里没有用 @Preview，release 包不包含这两个库
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}
