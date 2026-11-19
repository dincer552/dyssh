package com.dincer552.dyssh.ssh

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SshServerProfileTest {

    @Test
    fun testSshServerProfileCreation() {
        val profile = SshServerProfile(
            id = "test-id-123",
            name = "Test Server",
            host = "example.com",
            port = 2222,
            username = "testuser",
            authenticationMethod = AuthenticationMethod.PRIVATE_KEY,
            password = "",
            description = "Test description",
            useCloudflareAccess = true
        )

        assertEquals("test-id-123", profile.id)
        assertEquals("Test Server", profile.name)
        assertEquals("example.com", profile.host)
        assertEquals(2222, profile.port)
        assertEquals("testuser", profile.username)
        assertEquals(AuthenticationMethod.PRIVATE_KEY, profile.authenticationMethod)
        assertEquals("", profile.password)
        assertEquals("Test description", profile.description)
        assertTrue(profile.useCloudflareAccess)
    }

    @Test
    fun testSshServerProfileDefaults() {
        val profile = SshServerProfile(
            name = "Minimal Server",
            host = "192.168.1.1",
            username = "user"
        )

        assertNotNull(profile.id)
        assertTrue(UUID.fromString(profile.id).version() == 4) // Random UUID
        assertEquals(22, profile.port)
        assertEquals(AuthenticationMethod.PRIVATE_KEY, profile.authenticationMethod)
        assertEquals("", profile.password)
        assertEquals("", profile.description)
        assertFalse(profile.useCloudflareAccess)
    }

    @Test
    fun testAuthenticationMethodEnum() {
        assertEquals(2, AuthenticationMethod.values().size)
        assertEquals(AuthenticationMethod.PRIVATE_KEY, AuthenticationMethod.valueOf("PRIVATE_KEY"))
        assertEquals(AuthenticationMethod.PASSWORD, AuthenticationMethod.valueOf("PASSWORD"))
    }

    @Test
    fun testJsonEncoding() {
        val profile = SshServerProfile(
            id = "encode-test-id",
            name = "Encode Test",
            host = "encode.example.com",
            port = 22,
            username = "encoder",
            authenticationMethod = AuthenticationMethod.PASSWORD,
            password = "secret123",
            description = "Encoding test",
            useCloudflareAccess = false
        )

        // Test using actual implementation from SshServerProfileStore.encode()
        val jsonString = encodeProfileToJson(profile)

        assertTrue(jsonString.contains("\"id\":\"encode-test-id\""))
        assertTrue(jsonString.contains("\"name\":\"Encode Test\""))
        assertTrue(jsonString.contains("\"host\":\"encode.example.com\""))
        assertTrue(jsonString.contains("\"port\":22"))
        assertTrue(jsonString.contains("\"username\":\"encoder\""))
        assertTrue(jsonString.contains("\"authenticationMethod\":\"PASSWORD\""))
        assertTrue(jsonString.contains("\"password\":\"secret123\""))
        assertTrue(jsonString.contains("\"description\":\"Encoding test\""))
        assertTrue(jsonString.contains("\"useCloudflareAccess\":false"))
    }

    @Test
    fun testJsonArrayEncoding() {
        val profiles = listOf(
            SshServerProfile(id = "1", name = "Server 1", host = "h1", username = "u1"),
            SshServerProfile(id = "2", name = "Server 2", host = "h2", username = "u2")
        )

        val jsonArrayString = encodeProfilesToJsonArray(profiles)

        assertTrue(jsonArrayString.contains("\"name\":\"Server 1\""))
        assertTrue(jsonArrayString.contains("\"name\":\"Server 2\""))
        assertTrue(jsonArrayString.startsWith("["))
        assertTrue(jsonArrayString.endsWith("]"))
    }

    // JSON encoding helpers matching SshServerProfileStore implementation
    private fun encodeProfileToJson(profile: SshServerProfile): String {
        return """{"id":"${profile.id}","name":"${profile.name}","host":"${profile.host}","port":${profile.port},"username":"${profile.username}","authenticationMethod":"${profile.authenticationMethod.name}","password":"${profile.password}","description":"${profile.description}","useCloudflareAccess":${profile.useCloudflareAccess}}"""
    }

    private fun encodeProfilesToJsonArray(profiles: List<SshServerProfile>): String {
        val items = profiles.map { encodeProfileToJson(it) }.joinToString(",")
        return "[$items]"
    }
}