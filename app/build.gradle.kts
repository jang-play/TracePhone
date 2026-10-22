import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9는 Kotlin 컴파일을 내장하므로 kotlin-android 플러그인을 적용하지 않는다.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// release 서명에 쓸 값을 읽는다. 키스토어 파일과 비밀번호는 저장소에 넣지 않는다.
// 로컬 빌드는 프로젝트 루트의 keystore.properties에서, CI 빌드는 환경변수에서 받는다.
// 둘 다 없으면 서명 설정을 만들지 않아 서명되지 않은 APK가 나온다.
val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    releaseSigningProperties.getProperty(propertyName) ?: System.getenv(environmentName)

val releaseKeystorePath = releaseSigningValue("storeFile", "RELEASE_STORE_FILE")

// 새 버전 기록을 읽을 주소. gradle.properties에서 받고, 없으면 빈 문자열이 되어
// 앱이 업데이트 확인 기능을 내보이지 않는다.
val updateManifestUrl = providers.gradleProperty("tracephone.updateManifestUrl").getOrElse("")

android {
    namespace = "com.dksystems.tracephone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dksystems.tracephone"

        // java.time(LocalDate)을 desugaring 없이 사용하기 위해 API 26을 최소 사양으로 둔다.
        minSdk = 26
        targetSdk = 37

        versionCode = 9
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }

    // 기기에 이미 설치된 앱과 서명이 같아야 adb install -r로 덮어쓸 수 있다.
    // 서명이 달라지면 삭제 후 재설치만 가능해지고, 그때 누적된 사용 기록이 사라진다.
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseSigningValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 서명 정보가 없는 환경에서도 빌드 자체는 되도록 조건부로 붙인다.
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = true
            isShrinkResources = true
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

        // 업데이트 기록 주소와 설치된 버전을 코드에서 읽기 위해 필요하다.
        buildConfig = true
    }

    // 내보낸 Room 스키마를 계측 테스트에서 읽을 수 있게 자산으로 포함한다.
    // 마이그레이션이 실제로 예전 DB를 열 수 있는지 검증하는 데 필요하다.
    sourceSets.getByName("androidTest") {
        assets.directories.add("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin의 jvmTarget은 위 targetCompatibility 값을 자동으로 따르므로 따로 지정하지 않는다.

ksp {
    // Room 스키마를 내보내 이후 마이그레이션 검증에 사용한다.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // 앱과 테스트의 코루틴 버전을 한곳에서 맞춘다.
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(platform(libs.kotlinx.coroutines.bom))
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.kotlinx.coroutines.bom))
    androidTestImplementation(platform(libs.kotlinx.serialization.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
