package com.assassinlauncher.launcher.settings

/**
 * Mirrors CREDITS.md at the project root. Kept as a small manual list
 * rather than parsing the markdown file at runtime - this doesn't change
 * often enough to justify that complexity, and a static list is something
 * I can actually verify is correct by reading it, unlike a parser I can't
 * test here.
 */
data class CreditEntry(val name: String, val license: String, val note: String? = null)

object Credits {
    val entries = listOf(
        CreditEntry(
            "Turnip (Mesa/Freedreno)",
            "MIT",
            "Adreno GPU Vulkan driver fallback"
        ),
        CreditEntry(
            "OpenJDK",
            "GPL-2.0 with Classpath Exception",
            "bundled Java runtime"
        ),
        CreditEntry(
            "libadrenotools",
            "BSD-2-Clause",
            "custom Vulkan driver loading"
        ),
        CreditEntry(
            "GL4ES / HolyGL4ES / Krypton Wrapper (NG-GL4ES)",
            "MIT",
            "OpenGL translation layer"
        ),
        CreditEntry(
            "MobileGlues",
            "LGPL-2.1",
            "OpenGL translation layer"
        ),
        CreditEntry(
            "Zink",
            "MIT",
            "part of the Mesa project"
        ),
        CreditEntry(
            "ANGLE",
            "BSD-3-Clause",
            "Google graphics library"
        )
    )
}
