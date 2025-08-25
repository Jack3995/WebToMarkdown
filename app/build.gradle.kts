plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jack3995.webtomarkdown"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jack3995.webtomarkdown"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "1.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Новый блок настроек Kotlin для замены kotlinOptions
kotlin {
    // Устанавливаем JVM-таргет через jvmToolchain
    jvmToolchain(17)

    // Если нужны дополнительные опции компилятора, можно добавить сюда
    compilerOptions {
        // Например:
        // freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("com.squareup.okhttp3:okhttp:5.1.0")  // Для загрузки HTML
    implementation("org.jsoup:jsoup:1.21.1")             // Для парсинга HTML
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.compose.material3:material3:1.3.2")
}
