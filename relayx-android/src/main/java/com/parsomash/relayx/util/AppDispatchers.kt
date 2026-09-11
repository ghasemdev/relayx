package com.parsomash.relayx.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Encapsulates CoroutineDispatchers to decouple business logic,
 * repositories, and ViewModels from hardcoded dispatchers for seamless testing.
 */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main
)
