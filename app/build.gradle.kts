import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

val keystoreFile = (
    System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("storeFile")
    )?.let { raw ->
        // keystore.properties의 경로는 프로젝트 루트 기준으로 읽는다(모듈 기준 아님).
        val file = File(raw)
        if (file.isAbsolute) file else rootProject.file(raw)
    }
val hasReleaseKey = keystoreFile?.isFile == true

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop)

android {
    namespace = "com.sunuhwi.poe2dbkr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sunuhwi.poe2dbkr"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.1.5"

        resourceConfigurations += listOf("ko", "en")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // debug 서명도 프로젝트 안 키로 고정한다 (빌드 환경의 HOME 경로에 따라 서명 키가 달라지지 않게).
        getByName("debug") {
            val debugKey = rootProject.file("keystore/debug.keystore")
            if (debugKey.isFile) {
                storeFile = debugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (hasReleaseKey) {
                storeFile = keystoreFile
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // debug에서는 구글 공식 시험용 전면 광고 단위를 쓴다 (실제 광고를 누르면 정책 위반).
            buildConfigField("String", "AD_INTERSTITIAL_UNIT", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            isMinifyEnabled = false
            // PoE2DB_KR_PoB_Interstitial
            buildConfigField("String", "AD_INTERSTITIAL_UNIT", "\"ca-app-pub-5800168651924862/4166765580\"")
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        viewBinding = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
