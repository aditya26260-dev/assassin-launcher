// AGP 9.x ships built-in Kotlin support, so there's no separate
// org.jetbrains.kotlin.android plugin declared here or in app/build.gradle.kts.
// The Compose *compiler* is a separate concern though - since Kotlin 2.0
// it's not bundled into "Kotlin support" the way the rest of the
// language is, and needs its own plugin whenever a module turns Compose
// on. AGP 9.1.1 bundles Kotlin 2.2.10 (confirmed against AGP's own
// release notes, not assumed), so this is pinned to match exactly -
// Compose compiler plugin versions must match Kotlin the way LWJGL
// versions must match a Minecraft version elsewhere in this project.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
