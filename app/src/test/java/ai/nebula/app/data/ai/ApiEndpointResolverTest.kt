package ai.nebula.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointResolverTest {
    @Test
    fun appendsCompletionPathToVersionedBase() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ApiEndpointResolver.resolve("https://api.example.com/v1/")
        )
    }

    @Test
    fun doesNotDuplicateExistingCompletionPath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ApiEndpointResolver.resolve("https://api.example.com/v1/chat/completions")
        )
    }

    @Test
    fun rejectsInsecureOrMalformedEndpoints() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiEndpointResolver.resolve("http://api.example.com/v1")
        }
    }
}
