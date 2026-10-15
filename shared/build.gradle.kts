import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.saysharp.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        withHostTestBuilder {}.configure {}
    }
    // Add iOS targets and framework export when iOS development begins.
    // All lessons and UI below remain in commonMain; no Xcode setup is needed now.
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
