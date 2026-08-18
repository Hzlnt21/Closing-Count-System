package com.closingcount.app

import com.closingcount.app.data.local.AppMetadataEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AppMetadataEntityTest {
    @Test
    fun metadataRetainsKeyAndValue() {
        val metadata = AppMetadataEntity(
            key = "schema_status",
            value = "ready",
        )

        assertEquals("schema_status", metadata.key)
        assertEquals("ready", metadata.value)
    }
}
