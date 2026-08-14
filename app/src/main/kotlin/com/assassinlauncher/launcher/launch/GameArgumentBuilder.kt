package com.assassinlauncher.launcher.launch

import com.assassinlauncher.launcher.jvm.ArgumentTemplate
import com.assassinlauncher.launcher.jvm.MinecraftVersionDetails
import com.assassinlauncher.launcher.jvm.VersionRuleEvaluator

/**
 * Turns Mojang's argument templates - or the legacy flat string, for
 * pre-1.13 versions - into real JVM/game argument lists, substituting
 * ${token} placeholders with actual values. Rule-gated entries are kept
 * or dropped via VersionRuleEvaluator, extended this session to also
 * handle the "features" condition these arrays use (os-only rules were
 * already correctly handled from the library-filtering side). Real
 * coverage of the argument-rule schema, not the "only handle plain
 * strings, TODO the rest" shortcut Amethyst's own shipped code actually
 * takes for arguments.game.
 */
object GameArgumentBuilder {

    private val TOKEN_REGEX = Regex("\\$\\{([a-zA-Z0-9_]+)}")

    fun buildJvmArgs(
        details: MinecraftVersionDetails,
        substitutions: Map<String, String>,
        activeFeatures: Map<String, Boolean> = emptyMap()
    ): List<String> {
        val templates = details.jvmArguments.ifEmpty { LEGACY_DEFAULT_JVM_ARGS }
        return resolve(templates, substitutions, activeFeatures)
    }

    fun buildGameArgs(
        details: MinecraftVersionDetails,
        substitutions: Map<String, String>,
        activeFeatures: Map<String, Boolean> = emptyMap()
    ): List<String> {
        if (details.gameArguments.isNotEmpty()) {
            return resolve(details.gameArguments, substitutions, activeFeatures)
        }
        // Pre-1.13: one pre-formatted, space-separated string with the
        // same ${token} substitution but no per-token rule gating - the
        // whole string is unconditional by construction.
        val legacy = details.legacyMinecraftArguments ?: return emptyList()
        return legacy.split(" ")
            .filter { it.isNotBlank() }
            .map { substitute(it, substitutions) }
    }

    private fun resolve(
        templates: List<ArgumentTemplate>,
        substitutions: Map<String, String>,
        activeFeatures: Map<String, Boolean>
    ): List<String> = templates
        .filter { VersionRuleEvaluator.isAllowed(it.rules, activeFeatures) }
        .flatMap { it.values }
        .map { substitute(it, substitutions) }

    private fun substitute(raw: String, substitutions: Map<String, String>): String =
        TOKEN_REGEX.replace(raw) { match ->
            // Unknown tokens are left as-is rather than blanked out - a
            // blank silently produces a subtly wrong argument list; a
            // literal "${unknown_token}" reaching the JVM is at least
            // loud enough to notice and fix.
            substitutions[match.groupValues[1]] ?: match.value
        }

    // Only a defensive fallback: every version this project can actually
    // launch is 1.13+ (the LWJGL3 boundary AndroidLwjglProvider covers),
    // and every real manifest in that range ships a full jvmArguments
    // array, so this path shouldn't normally be exercised.
    private val LEGACY_DEFAULT_JVM_ARGS = listOf(
        ArgumentTemplate(listOf("-Djava.library.path=\${natives_directory}"), null),
        ArgumentTemplate(listOf("-cp"), null),
        ArgumentTemplate(listOf("\${classpath}"), null)
    )
}
