plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val geckoviewVersion = "136.0.20250227124745"

android {
    namespace = "com.example.waspbrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.waspbrowser"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    androidResources {
        // WASM não pode ser comprimido — Android corromperia o arquivo no APK
        // O WebViewClient serve diretamente dos assets sem descompressão
        noCompress += listOf("wasm", "js")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "**/*.kotlin_builtins",
                "**/*.kotlin_metadata",
                "DebugProbesKt.bin",
            )
        }
    }
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val name = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val baseVersionCode = abiCodes[name] ?: 0
            output.versionCode.set(baseVersionCode * 1000 + (variant.outputs.first().versionCode.get() ?: 1))
        }
    }
}

dependencies {
    // Versão estável do AdMob
    // anuncios de video: Start.io
    implementation("com.startapp:inapp-sdk:5.+")

    implementation("androidx.webkit:webkit:1.10.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}