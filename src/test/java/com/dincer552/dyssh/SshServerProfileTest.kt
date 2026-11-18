package com.dincer552.dyssh.ssh

import org.json.JSONArray
import org.json.JSONObject
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

        val json = JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("host", profile.host)
            .put("port", profile.port)
            .put("username", profile.username)
            .put("authenticationMethod", profile.authenticationMethod.name)
            .put("password", profile.password)
            .put("description", profile.description)
            .put("useCloudflareAccess", profile.useCloudflareAccess)

        assertEquals("encode-test-id", json.getString("id"))
        assertEquals("Encode Test", json.getString("name"))
        assertEquals("encode.example.com", json.getString("host"))
        assertEquals(22, json.getInt("port"))
        assertEquals("encoder", json.getString("username"))
        assertEquals("PASSWORD", json.getString("authenticationMethod"))
        assertEquals("secret123", json.getString("password"))
        assertEquals("Encoding test", json.getString("description"))
        assertFalse(json.getBoolean("useCloudflareAccess"))
    }

    @Test
    fun testJsonArrayEncoding() {
        val profiles = listOf(
            SshServerProfile(id = "1", name = "Server 1", host = "h1", username = "u1"),
            SshServerProfile(id = "2", name = "Server 2", host = "h2", username = "u2")
        )

        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("host", profile.host)
                    .put("port", profile.port)
                    .put("username", profile.username)
                    .put("authenticationMethod", profile.authenticationMethod.name)
                    .put("password", profile.password)
                    .put("description", profile.description)
                    .put("useCloudflareAccess", profile.useCloudflareAccess)
            )
        }

        assertEquals(2, array.length())
        assertEquals("Server 1", array.getJSONObject(0).getString("name"))
        assertEquals("Server 2", array.getJSONObject(1).getString("name"))
    }
}