package com.livingroomhq.translate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionPipelineLifecycleTest {
    @Test fun `restart waits for inference and backend release before preparing again`() = runTest {
        val lifecycle = CaptionPipelineLifecycle(StandardTestDispatcher(testScheduler))
        val nativeFinished = CompletableDeferred<Unit>()
        val releaseFinished = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        lifecycle.start(backgroundScope, run = {
            events += "prepare first"
            launch {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { nativeFinished.await(); events += "native done" } }
            }
            awaitCancellation()
        }, release = { events += "release first"; releaseFinished.await() })
        runCurrent()
        lifecycle.stop()
        lifecycle.start(backgroundScope, run = { events += "prepare second"; awaitCancellation() }, release = { events += "release second" })
        runCurrent()
        assertEquals(listOf("prepare first"), events)
        nativeFinished.complete(Unit)
        runCurrent()
        assertEquals(listOf("prepare first", "native done", "release first"), events)
        releaseFinished.complete(Unit)
        runCurrent()
        assertEquals("prepare second", events.last())
        lifecycle.stop()
        runCurrent()
        assertEquals("release second", events.last())
    }

    @Test fun `stopping a queued replacement never prepares or releases its backends`() = runTest {
        val lifecycle = CaptionPipelineLifecycle(StandardTestDispatcher(testScheduler))
        val releaseFinished = CompletableDeferred<Unit>()
        var preparations = 0
        var releases = 0
        val run: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = { preparations++; awaitCancellation() }
        val release: suspend () -> Unit = { releases++; releaseFinished.await() }
        lifecycle.start(backgroundScope, run, release)
        runCurrent()
        lifecycle.stop()
        lifecycle.start(backgroundScope, run, release)
        runCurrent()
        lifecycle.stop()
        releaseFinished.complete(Unit)
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(1, releases)
    }

    @Test fun `parent cancellation releases children exactly once and repeated starts are ignored`() = runTest {
        val parent = kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + kotlinx.coroutines.Job())
        val lifecycle = CaptionPipelineLifecycle(StandardTestDispatcher(testScheduler))
        var preparations = 0
        var releases = 0
        val run: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = { preparations++; awaitCancellation() }
        val release: suspend () -> Unit = { releases++; withContext(NonCancellable) { } }
        lifecycle.start(parent, run, release)
        lifecycle.start(parent, run, release)
        runCurrent()
        parent.cancel()
        runCurrent()
        lifecycle.stop()
        lifecycle.stop()
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(1, releases)
        assertFalse(lifecycle.isRunning())
    }
    @Test fun `completion cleanup runs even when cancellation happens before startup`() = runTest {
        val lifecycle = CaptionPipelineLifecycle(StandardTestDispatcher(testScheduler))
        var preparations = 0
        var releases = 0
        var completions = 0
        lifecycle.start(backgroundScope, run = { preparations++ }, release = { releases++ }, onComplete = { completions++ })
        lifecycle.stop()
        runCurrent()
        assertEquals(0, preparations)
        assertEquals(0, releases)
        assertEquals(1, completions)
    }

}
