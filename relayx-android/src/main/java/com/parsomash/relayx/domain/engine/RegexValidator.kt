package com.parsomash.relayx.domain.engine

import java.util.regex.PatternSyntaxException

object RegexValidator {

    /**
     * Validates if the given pattern string compiles to a valid regular expression.
     * @return null if valid, or a descriptive error message if invalid.
     */
    fun validatePattern(pattern: String?): String? {
        if (pattern.isNullOrBlank()) return null
        return try {
            Regex(pattern)
            null
        } catch (e: PatternSyntaxException) {
            e.description ?: "Invalid regular expression syntax"
        } catch (e: Exception) {
            e.message ?: "Invalid regular expression"
        }
    }

    /**
     * Attempts to match and extract a capture group from [text] using [pattern].
     * If group [groupIndex] exists and was captured, its value is returned.
     * Otherwise, if the regex matched, group 0 (the full match) is returned.
     * If there was no match or pattern is invalid, returns null.
     */
    fun extract(pattern: String, text: String, groupIndex: Int = 1): String? {
        return try {
            val regex = Regex(pattern)
            val matchResult = regex.find(text) ?: return null
            if (matchResult.groups.size > groupIndex && matchResult.groups[groupIndex] != null) {
                matchResult.groups[groupIndex]?.value
            } else {
                matchResult.value
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tests if [text] matches or contains the regex [pattern].
     */
    fun matches(pattern: String, text: String): Boolean {
        return try {
            Regex(pattern).containsMatchIn(text)
        } catch (_: Exception) {
            false
        }
    }
}
