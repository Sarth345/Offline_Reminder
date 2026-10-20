package com.example.offlinereminder.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * Test implementation of [DispatcherProvider] providing a shared [TestDispatcher]
 * for deterministic coroutine testing.
 */
class TestDispatcherProvider(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = testDispatcher
    override val io: CoroutineDispatcher get() = testDispatcher
    override val default: CoroutineDispatcher get() = testDispatcher
    override val unconfined: CoroutineDispatcher get() = testDispatcher
}
