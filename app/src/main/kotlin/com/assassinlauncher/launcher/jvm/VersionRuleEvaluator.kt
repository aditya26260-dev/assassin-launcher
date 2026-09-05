package com.assassinlauncher.launcher.jvm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mojang's version JSON gates libraries and arguments behind rule lists
 * like [{"action":"allow","os":{"name":"osx"}}]. Algorithm confirmed
 * directly against PrismLauncher's real Rule.cpp rather than assumed from
 * memory: each rule either doesn't apply (its os/arch condition doesn't
 * match the current platform - skipped, doesn't change the result) or
 * applies and sets the result to its action (allow/disallow). The *last*
 * applicable rule in the list wins. No rules at all means allowed by
 * default - Mojang's own convention for entries with no rules array.
 *
 * This project only ever targets one platform (arm64 Android, presented
 * to this logic as "linux" since that's what the JVM/native layer
 * actually looks like from Mojang's manifest's perspective) - simpler
 * than a real cross-platform launcher needs, stated plainly rather than
 * pretending to support platforms this project will never run on.
 *
 * The modern arguments.game/arguments.jvm arrays (1.13+) use this same
 * rule shape but add a "features" condition alongside (or instead of)
 * "os" - e.g. [{"action":"allow","features":{"is_demo_user":true}}].
 * Library rules never carry "features"; passing none (the default) keeps
 * every existing library-filtering call site unchanged.
 */
object VersionRuleEvaluator {

    private const val CURRENT_OS = "linux"

    // Real manifests only ever gate on arch for legacy x86/x86_64 desktop
    // quirks (e.g. -Xss1M's {"os":{"arch":"x86"}}) - none of those apply to
    // this arm64-only Android project, so this deliberately never matches.
    private const val CURRENT_ARCH = "arm64"

    fun isAllowed(rules: JSONArray?, activeFeatures: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules == null || rules.length() == 0) return true

        // A present (non-empty) rules array makes this value conditional:
        // exclude it unless some rule actually grants "allow" for the
        // current platform. Verified against the real crash: values whose
        // only rule is {"os":{"name":"osx"}} (-XstartOnFirstThread) or
        // {"os":{"name":"windows"}} (HeapDumpPath) were reaching the JVM on
        // Android because a default of true meant an unmatched rule left
        // the value included instead of excluding it - the last-applicable-
        // rule-wins logic never got a chance to run when nothing applied.
        var result = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val action = rule.optString("action", "allow")
            val osObject = rule.optJSONObject("os")
            val featuresObject = rule.optJSONObject("features")

            val osApplies = if (osObject == null) {
                true
            } else {
                val osName = osObject.optString("name", "")
                val osArch = osObject.optString("arch", "")
                // Previously checked only "name" - {"os":{"arch":"x86"}}
                // (no "name" key) fell through as blank/unconstrained and
                // always matched, which is how -Xss1M reached an arm64
                // device despite being gated to x86 specifically.
                (osName.isBlank() || osName == CURRENT_OS) &&
                    (osArch.isBlank() || osArch == CURRENT_ARCH)
            }

            // A feature not present in activeFeatures is treated as "not
            // enabled" (false) - matches every reference launcher's
            // default posture of features being opt-in, not opt-out.
            val featuresApply = if (featuresObject == null) {
                true
            } else {
                featuresObject.keys().asSequence().all { key ->
                    activeFeatures.getOrDefault(key, false) == featuresObject.optBoolean(key)
                }
            }

            if (osApplies && featuresApply) {
                result = action == "allow"
            }
        }
        return result
    }
}
