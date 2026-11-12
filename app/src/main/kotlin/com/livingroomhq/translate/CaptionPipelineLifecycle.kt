package com.livingroomhq.translate

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A replacement session waits for all old workers and backend cleanup. */
internal class CaptionPipelineLifecycle(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private val mutex = Mutex()
    private var job: Job? = null

    @Synchronized fun isRunning(): Boolean = job?.isActive == true

    @Synchronized fun start(
        parentScope: CoroutineScope,
        run: suspend CoroutineScope.() -> Unit,
        release: suspend () -> Unit,
        onComplete: () -> Unit = {},
    ) {
        if (job?.isActive == true) return
        job = parentScope.launch(dispatcher) {
            mutex.withLock {
                try {
                    // Child ASR/MT jobs finish before release, including native work
                    // that cannot observe cancellation until inference returns.
                    coroutineScope(run)
                } finally {
                    withContext(NonCancellable) { release() }
                }
            }
        }.also { it.invokeOnCompletion { onComplete() } }
    }

    @Synchronized fun stop() {
        job?.cancel()
        job = null
    }
}
