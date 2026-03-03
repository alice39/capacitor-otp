package com.alice0.capacitor.otp

/**
 * Utility for extracting OTP codes from SMS text using regex.
 *
 * The default pattern `(\d{4,8})` matches 4–8 digit numeric codes, which covers
 * the vast majority of bank / service OTP messages.
 *
 * A custom pattern can be supplied via [StartListeningOptions.otpPattern] from JS.
 * It **must** contain exactly one capture group.
 */
object OtpParser {

    private const val DEFAULT_PATTERN = "(\\d{4,8})"

    /**
     * Extract the first OTP match from [message] using [pattern].
     *
     * @param message The full SMS text.
     * @param pattern A regex string with one capture group. Falls back to [DEFAULT_PATTERN]
     *                if `null` or if the supplied regex is invalid.
     * @return The captured OTP string, or `null` if no match was found.
     */
    fun extract(message: String, pattern: String? = null): String? {
        return try {
            val regex = Regex(pattern ?: DEFAULT_PATTERN)
            regex.find(message)?.groupValues?.getOrNull(1)
        } catch (_: Exception) {
            // Invalid regex supplied — fall back to the default pattern
            try {
                val regex = Regex(DEFAULT_PATTERN)
                regex.find(message)?.groupValues?.getOrNull(1)
            } catch (_: Exception) {
                null
            }
        }
    }
}
