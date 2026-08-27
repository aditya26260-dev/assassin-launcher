plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.assassinlauncher.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.assassinlauncher.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                // Unset, this defaults to the static STL, which means no
                // libc++_shared.so ever gets bundled into the APK at all -
                // confirmed the AngelAuraMC JDK archive itself doesn't ship
                // one either (checked the actual jre25-android-arm64.tar.xz
                // contents directly), so libjli.so's dlopen for it had
                // nowhere to find it. This produces the .so; see
                // NativeBridge's init block for the other half (loading it
                // before libjli.so needs it).
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            // libadrenotools' own header docs are explicit: this MUST be
            // true. With false, native libs aren't extracted to a real
            // nativeLibraryDir path at install time, and adrenotools_open_
            // libvulkan's hookLibDir requirement silently fails - the app
            // would still run, just with the driver hook not working.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Vendored, not a Maven dependency - com.github.steveice10:opennbt:1.0
    // is nominally on Maven Central but didn't actually resolve on a real
    // build (every class from it came back "Unresolved reference").
    // ZalithLauncher2, where this dependency choice came from in the
    // first place, vendors the compiled jar directly rather than
    // depending on it remotely at all - not a coincidence, their own
    // real-world experience with this exact artifact. Using their same
    // jar (1.6, newer than the 1.0 this project first pointed at) rather
    // than re-guessing a coordinate: confirmed compatible by cross-
    // checking their own real usage against this project's ServerRepository.kt
    // before adopting it, not assumed.
    implementation(fileTree("libs") { include("*.jar") })

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
