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

    fun isAllowed(rules: JSONArray?, activeFeatures: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules == null || rules.length() == 0) return true

        var result = true
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val action = rule.optString("action", "allow")
            val osObject = rule.optJSONObject("os")
            val featuresObject = rule.optJSONObject("features")

            val osApplies = if (osObject == null) {
                true
            } else {
                val osName = osObject.optString("name", "")
                osName.isBlank() || osName == CURRENT_OS
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
