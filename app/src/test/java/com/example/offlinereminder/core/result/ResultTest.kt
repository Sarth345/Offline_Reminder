package com.example.offlinereminder.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResultTest {

    @Test
    fun `success returns expected data and properties`() {
        val result: Result<String> = Result.Success("Hello")

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("Hello", result.getOrNull())
        assertEquals("Hello", result.getOrDefault("Fallback"))
        assertEquals("Hello", result.getOrThrow())
    }

    @Test
    fun `failure returns expected error and properties`() {
        val exception = IllegalStateException("Test error")
        val result: Result<String> = Result.Failure(exception)

        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertEquals("Fallback", result.getOrDefault("Fallback"))

        try {
            result.getOrThrow()
            fail("Expected exception was not thrown")
        } catch (e: IllegalStateException) {
            assertEquals("Test error", e.message)
        }
    }

    @Test
    fun `map transforms success value`() {
        val success: Result<Int> = Result.Success(10)
        val mapped = success.map { it * 2 }

        assertEquals(20, mapped.getOrNull())
    }

    @Test
    fun `map propagates failure without running block`() {
        var executed = false
        val failure: Result<Int> = Result.Failure(RuntimeException("fail"))
        val mapped = failure.map {
            executed = true
            it * 2
        }

        assertFalse(executed)
        assertTrue(mapped.isFailure)
    }

    @Test
    fun `flatMap chains successfully`() {
        val success: Result<Int> = Result.Success(5)
        val flatMapped = success.flatMap { Result.Success(it.toString()) }

        assertEquals("5", flatMapped.getOrNull())
    }

    @Test
    fun `runCatching captures exceptions into Failure`() {
        val success = Result.runCatching { "ok" }
        assertTrue(success.isSuccess)
        assertEquals("ok", success.getOrNull())

        val failure = Result.runCatching {
            throw IllegalArgumentException("bad argument")
        }
        assertTrue(failure.isFailure)
        assertEquals("bad argument", (failure as Result.Failure).message)
    }

    @Test
    fun `fold dispatches correctly`() {
        val success: Result<Int> = Result.Success(42)
        val successStr = success.fold(
            onSuccess = { "Value: $it" },
            onFailure = { "Error: ${it.message}" }
        )
        assertEquals("Value: 42", successStr)

        val failure: Result<Int> = Result.Failure(RuntimeException("boom"))
        val failureStr = failure.fold(
            onSuccess = { "Value: $it" },
            onFailure = { "Error: ${it.message}" }
        )
        assertEquals("Error: boom", failureStr)
    }
}
