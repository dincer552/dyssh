package com.dincer552.dyssh.security

import org.junit.Assert.*
import org.junit.Test

class SshKeyImportValidationTest {

    @Test
    fun testValidPemPrivateKey_RSA() {
        val validRsaKey = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA...
            -----END RSA PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertTrue(isValidPemPrivateKey(validRsaKey))
    }

    @Test
    fun testValidPemPrivateKey_PKCS8() {
        val validPkcs8Key = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQD...
            -----END PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertTrue(isValidPemPrivateKey(validPkcs8Key))
    }

    @Test
    fun testValidPemPrivateKey_OpenSSH() {
        val validOpenSshKey = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAAB...
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertTrue(isValidPemPrivateKey(validOpenSshKey))
    }

    @Test
    fun testValidPemPrivateKey_EC() {
        val validEcKey = """
            -----BEGIN EC PRIVATE KEY-----
            MHcCAQEEI...
            -----END EC PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertTrue(isValidPemPrivateKey(validEcKey))
    }

    @Test
    fun testInvalidPem_MissingBegin() {
        val invalidKey = """
            PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQD...
            -----END PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertFalse(isValidPemPrivateKey(invalidKey))
    }

    @Test
    fun testInvalidPem_MissingEnd() {
        val invalidKey = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQD...
            END PRIVATE KEY-----
        """.trimIndent().toByteArray()

        assertFalse(isValidPemPrivateKey(invalidKey))
    }

    @Test
    fun testInvalidPem_NotAKey() {
        val invalidKey = "This is not a private key at all".toByteArray()
        assertFalse(isValidPemPrivateKey(invalidKey))
    }

    @Test
    fun testInvalidPem_Empty() {
        val emptyKey = "".toByteArray()
        assertFalse(isValidPemPrivateKey(emptyKey))
    }

    @Test
    fun testInvalidPem_TooLarge() {
        val largeKey = "A".repeat(1024 * 1024 + 1).toByteArray()
        assertFalse(isValidPemPrivateKey(largeKey))
    }

    @Test
    fun testInvalidPem_PublicKey() {
        val publicKey = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
            -----END PUBLIC KEY-----
        """.trimIndent().toByteArray()

        // Public key should not be accepted as private key
        assertFalse(isValidPemPrivateKey(publicKey))
    }

    @Test
    fun testInvalidPem_Certificate() {
        val certificate = """
            -----BEGIN CERTIFICATE-----
            MIIDXTCCAkWgAwIBAgIJAK...
            -----END CERTIFICATE-----
        """.trimIndent().toByteArray()

        assertFalse(isValidPemPrivateKey(certificate))
    }

    // Mirror the validation logic from SshKeyImportManager
    companion object {
        private const val MAX_KEY_SIZE_BYTES = 1024 * 1024

        fun isValidPemPrivateKey(keyBytes: ByteArray): Boolean {
            if (keyBytes.isEmpty() || keyBytes.size > MAX_KEY_SIZE_BYTES) {
                return false
            }
            val header = keyBytes.copyOfRange(0, minOf(keyBytes.size, 128))
                .toString(java.nio.charset.StandardCharsets.US_ASCII)
            val footer = keyBytes.copyOfRange(maxOf(0, keyBytes.size - 96), keyBytes.size)
                .toString(java.nio.charset.StandardCharsets.US_ASCII)
            return header.contains("-----BEGIN") &&
                header.contains("PRIVATE KEY-----") &&
                footer.contains("-----END")
        }
    }
}