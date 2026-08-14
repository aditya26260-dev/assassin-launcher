package com.assassinlauncher.launcher.instance

/**
 * Minecraft's versioning changed schemes somewhere between the 1.21.x line
 * and 26.2 (Phase 0 research confirmed 26.2 as a real, dated release, but
 * not the exact version history between the two schemes - that's past
 * what I have confident knowledge of). This is a simplified comparator
 * good enough to drive the two specific checks architecture 5.3 actually
 * needs (native Vulkan availability, the Zink+Mali exception), not a
 * general-purpose version parser. A real implementation should eventually
 * pull Mojang's actual version manifest at runtime instead of guessing at
 * version history from here - noted rather than quietly papered over.
 */
object MinecraftVersions {

    private val legacyVersionRegex = Regex("""^1\.(\d+)(?:\.(\d+))?""")
    private val newSchemeVersionRegex = Regex("""^(\d{2,})\.(\d+)""")

    fun isAtLeast26_2(version: String): Boolean {
        val newScheme = newSchemeVersionRegex.find(version) ?: return false
        val major = newScheme.groupValues[1].toIntOrNull() ?: return false
        val minor = newScheme.groupValues[2].toIntOrNull() ?: return false
        return major > 26 || (major == 26 && minor >= 2)
    }

    fun isAtMost1_16_5(version: String): Boolean {
        val legacy = legacyVersionRegex.find(version) ?: return false
        val minor = legacy.groupValues[1].toIntOrNull() ?: return false
        val patch = legacy.groupValues[2].toIntOrNull() ?: 0
        return minor < 16 || (minor == 16 && patch <= 5)
    }

    /** MobileGlues and LTW both share this floor (Phase 0 research) - below
     * it, they're not an option at all, not just de-prioritized. Any
     * new-scheme version (26.x+) is automatically past this since the
     * new scheme only started long after 1.17. */
    fun isAtLeast1_17(version: String): Boolean {
        if (newSchemeVersionRegex.find(version) != null) return true
        val legacy = legacyVersionRegex.find(version) ?: return false
        val minor = legacy.groupValues[1].toIntOrNull() ?: return false
        return minor >= 17
    }
}
