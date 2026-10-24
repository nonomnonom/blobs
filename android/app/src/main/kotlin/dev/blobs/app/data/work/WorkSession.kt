package dev.blobs.app.data.work

import dev.blobs.app.data.WorkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the one active application operation and the state rendered for it.
 *
 * Research turns and package transfers have separate lifecycles, but they must
 * never run concurrently because both report through the same application work
 * surface. This is the sole owner of that exclusivity.
 */
internal class WorkSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(WorkState())
    val state = mutable.asStateFlow()
    private var activeJob: Job? = null

    @Synchronized
    fun reserve(next: (WorkState) -> WorkState?): Boolean {
        val current = mutable.value
        if (!current.ready || current.busy) return false
        val reserved = next(current) ?: return false
        mutable.value = reserved
        return true
    }

    fun replace(value: WorkState) {
        mutable.value = value
    }

    fun update(transform: (WorkState) -> WorkState) {
        mutable.update(transform)
    }

    fun launchWork(block: suspend CoroutineScope.() -> Unit): Job {
        val launched = scope.launch(block = block)
        synchronized(this) {
            activeJob = launched
        }
        return launched
    }

    fun launchChild(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    @Synchronized
    fun currentJob(): Job? = activeJob

    @Synchronized
    fun beginMaintenance(): Boolean {
        if (mutable.value.busy) return false
        activeJob = null
        mutable.update { it.copy(busy = true, turnId = "", threadId = "") }
        return true
    }

    suspend fun mutate(block: suspend () -> Unit): Boolean {
        synchronized(this) {
            if (!mutable.value.ready || mutable.value.busy) return false
            activeJob = null
            mutable.update { it.copy(busy = true, turnId = "", threadId = "") }
        }
        try {
            block()
        } finally {
            mutable.update { it.copy(busy = false) }
        }
        return true
    }
}
