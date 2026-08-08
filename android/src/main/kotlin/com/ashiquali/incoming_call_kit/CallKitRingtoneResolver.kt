package com.ashiquali.incoming_call_kit

/**
 * Pure helpers for resolving which ringtone resource to play.
 * Returns null when the system default ringtone should be used.
 */
object CallKitRingtoneResolver {
    const val SYSTEM_DEFAULT = "system_ringtone_default"

    fun rawResourceName(ringtonePath: String?): String? {
        val trimmed = ringtonePath?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        if (lower == SYSTEM_DEFAULT || lower == "default" || lower == "system") {
            return null
        }

        val withoutExtension = trimmed.substringBeforeLast('.', missingDelimiterValue = trimmed)
        return withoutExtension.takeIf { it.isNotEmpty() }
    }

    fun usesSystemDefault(ringtonePath: String?): Boolean = rawResourceName(ringtonePath) == null
}
