package com.alice0.capacitor.otp

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [OtpParser].
 */
class OtpParserTest {

    // ── Default pattern ──────────────────────────────────────────────────

    @Test
    fun `extracts 6-digit code from typical bank SMS`() {
        val message = "Your verification code is 483920. Do not share this code."
        assertEquals("483920", OtpParser.extract(message))
    }

    @Test
    fun `extracts 4-digit code`() {
        val message = "Your OTP is 1234"
        assertEquals("1234", OtpParser.extract(message))
    }

    @Test
    fun `extracts 8-digit code`() {
        val message = "Use code 12345678 to continue"
        assertEquals("12345678", OtpParser.extract(message))
    }

    @Test
    fun `extracts first match when multiple codes present`() {
        val message = "Code: 123456. Expires in 300 seconds."
        assertEquals("123456", OtpParser.extract(message))
    }

    @Test
    fun `returns null when no numeric code in message`() {
        val message = "No code here, just text"
        assertNull(OtpParser.extract(message))
    }

    @Test
    fun `ignores 3-digit numbers (too short)`() {
        val message = "Error 404 occurred"
        assertNull(OtpParser.extract(message))
    }

    @Test
    fun `ignores 9+ digit numbers (too long for default pattern, extracts longest valid)`() {
        // Default pattern is (\d{4,8}), longest match wins within the capture
        val message = "Code: 123456789"
        // Regex engine will find "12345678" (first 8 chars match the pattern)
        assertEquals("12345678", OtpParser.extract(message))
    }

    // ── Custom patterns ─────────────────────────────────────────────────

    @Test
    fun `custom pattern extracts alphanumeric code`() {
        val message = "Your code: AB12CD"
        val result = OtpParser.extract(message, "([A-Z0-9]{6})")
        assertEquals("AB12CD", result)
    }

    @Test
    fun `custom pattern - 6 digits only`() {
        val message = "OTP: 123456 (valid for 10 min)"
        val result = OtpParser.extract(message, "(\\d{6})")
        assertEquals("123456", result)
    }

    @Test
    fun `returns null when custom pattern does not match`() {
        val message = "Hello, no code here"
        assertNull(OtpParser.extract(message, "(\\d{6})"))
    }

    // ── Invalid regex fallback ──────────────────────────────────────────

    @Test
    fun `falls back to default pattern on invalid regex`() {
        val message = "Your code is 483920"
        // Invalid regex (unclosed bracket) should fall back to default
        val result = OtpParser.extract(message, "(invalid[[")
        assertEquals("483920", result)
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun `handles empty message`() {
        assertNull(OtpParser.extract(""))
    }

    @Test
    fun `handles message with only whitespace`() {
        assertNull(OtpParser.extract("   \n\t  "))
    }

    @Test
    fun `extracts code from SMS Retriever format`() {
        val message = "Your code is 483920\n\nFA+9qCX9VSu"
        assertEquals("483920", OtpParser.extract(message))
    }
}
