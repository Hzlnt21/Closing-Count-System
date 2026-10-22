package com.closingcount.app

import com.closingcount.app.data.online.OnlineEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineEndpointTest {
    @Test fun canonicalOriginsDoNotSplitPartitions() {
        assertEquals("https://example.com", OnlineEndpoint("https://EXAMPLE.com:443/").origin)
        assertEquals("https://example.com:8443", OnlineEndpoint("https://example.com:8443").origin)
    }
    @Test fun pathsCredentialsAndQueriesAreNotEndpoints() {
        for (url in listOf("https://example.com/api", "https://user:pass@example.com", "https://example.com?x=1", "https://example.com#fragment", "file:///tmp", "https://example.com:0")) {
            assertThrows(IllegalArgumentException::class.java) { OnlineEndpoint(url) }
        }
    }
    @Test fun cleartextRequiresExplicitLocalDebugOptIn() {
        assertThrows(IllegalArgumentException::class.java) { OnlineEndpoint("http://127.0.0.1:4371") }
        assertThrows(IllegalArgumentException::class.java) { OnlineEndpoint("http://example.com", true) }
        assertEquals("http://127.0.0.1:4371", OnlineEndpoint("http://127.0.0.1:4371", true).origin)
    }
}
