package com.dincer552.dyssh.cloudflare

import org.junit.Assert.*
import org.junit.Test

class CloudflareUrlValidationTest {

    @Test
    fun testIsCloudflareAccessUrl_OfficialDomains() {
        // Test official Cloudflare Access domains
        assertTrue(isCloudflareAccessUrl("https://cloudflareaccess.com/login", "example.com"))
        assertTrue(isCloudflareAccessUrl("https://myapp.cloudflareaccess.com/auth", "example.com"))
        assertTrue(isCloudflareAccessUrl("https://sub.domain.cloudflareaccess.com/path", "example.com"))
        assertTrue(isCloudflareAccessUrl("https://cloudflareaccess.org/login", "example.com"))
        assertTrue(isCloudflareAccessUrl("https://team.cloudflareaccess.org/auth", "example.com"))
    }

    @Test
    fun testIsCloudflareAccessUrl_ConfiguredTunnelPath() {
        // Test configured tunnel access path
        assertTrue(isCloudflareAccessUrl("https://my-server.example.com/cdn-cgi/access/login", "my-server.example.com"))
        assertTrue(isCloudflareAccessUrl("https://ssh.internal/cdn-cgi/access/ssh", "ssh.internal"))
        assertTrue(isCloudflareAccessUrl("https://HOST.EXAMPLE.COM/cdn-cgi/access/", "host.example.com")) // Case insensitive
    }

    @Test
    fun testIsCloudflareAccessUrl_NegativeCases() {
        // Not HTTPS
        assertFalse(isCloudflareAccessUrl("http://cloudflareaccess.com/login", "example.com"))
        assertFalse(isCloudflareAccessUrl("ssh://cloudflareaccess.com/login", "example.com"))

        // Wrong domain
        assertFalse(isCloudflareAccessUrl("https://evil.com/login", "example.com"))
        assertFalse(isCloudflareAccessUrl("https://cloudflare.com/login", "example.com"))
        assertFalse(isCloudflareAccessUrl("https://access.cloudflare.com/login", "example.com"))

        // Wrong path for configured tunnel
        assertFalse(isCloudflareAccessUrl("https://example.com/other/path", "example.com"))
        assertFalse(isCloudflareAccessUrl("https://example.com/cdn-cgi/other", "example.com"))

        // Hostname mismatch for configured tunnel
        assertFalse(isCloudflareAccessUrl("https://other.com/cdn-cgi/access/login", "example.com"))

        // Invalid URLs
        assertFalse(isCloudflareAccessUrl("not-a-url", "example.com"))
        assertFalse(isCloudflareAccessUrl("", "example.com"))
    }

    @Test
    fun testSanitizeDiagnostic() {
        val line1 = "2024-01-15T10:00:00Z INF Starting cloudflared https://myapp.cloudflareaccess.com/login?token=abc123"
        val sanitized1 = sanitizeDiagnostic(line1)
        assertTrue(sanitized1.contains("[Cloudflare URL hidden]"))
        assertFalse(sanitized1.contains("abc123"))

        val line2 = "Authorization: Bearer secret-token-xyz"
        val sanitized2 = sanitizeDiagnostic(line2)
        assertTrue(sanitized2.contains("Bearer [redacted]"))
        assertFalse(sanitized2.contains("secret-token-xyz"))

        val line3 = "cookie=session-id-123; other=value"
        val sanitized3 = sanitizeDiagnostic(line3)
        assertTrue(sanitized3.contains("cookie=[redacted]"))
        assertFalse(sanitized3.contains("session-id-123"))

        val line4 = "Normal diagnostic message, tunnel started cleanly"
        val sanitized4 = sanitizeDiagnostic(line4)
        assertEquals(line4, sanitized4)
    }

    @Test
    fun testSanitizeDiagnostic_LengthLimit() {
        val longLine = "A".repeat(300)
        val sanitized = sanitizeDiagnostic(longLine)
        assertTrue(sanitized.length <= 240)
    }
}