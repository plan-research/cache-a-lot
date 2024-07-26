package org.plan.research.cachealot.cache

import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.async.KAsyncSolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.plan.research.cachealot.KBoolExprs
import kotlin.properties.Delegates
import kotlin.time.Duration

class KCachePipeline(val cache: KUnsatCache) {

    private var cacheTimeout: Duration? = null
    private var solverTimeout by Delegates.notNull<Duration>()

    fun withCacheTimeout(timeout: Duration): KCachePipeline {
        this.cacheTimeout = timeout
        return this
    }

    fun withSolverTimeout(timeout: Duration): KCachePipeline {
        this.solverTimeout = timeout
        return this
    }

    sealed interface Result

    object SolverUnknownResult : Result
    data class SolverSatResult(val model: KModel) : Result

    sealed interface UnsatResult : Result
    data class SolverUnsatResult(val unsatCore: KBoolExprs) : UnsatResult
    object CachedUnsat : UnsatResult

    suspend fun checkAsync(assertions: KBoolExprs, getSolver: () -> KAsyncSolver<*>): Result {
        val cacheTimeout = this.cacheTimeout
        val checkResult = if (cacheTimeout == null) {
            cache.check(assertions)
        } else {
            withTimeoutOrNull(cacheTimeout) {
                cache.check(assertions)
            }
        }
        if (checkResult == true) {
            return CachedUnsat
        }

        return getSolver().use { solver ->
            assertions.forEach { solver.assertAndTrackAsync(it) }
            val result = solver.checkAsync(solverTimeout)
            when (result) {
                KSolverStatus.SAT -> SolverSatResult(solver.modelAsync())
                KSolverStatus.UNSAT -> {
                    val core = solver.unsatCoreAsync()
                    cache.addUnsatCore(core)
                    SolverUnsatResult(core)
                }

                KSolverStatus.UNKNOWN -> SolverUnknownResult
            }
        }
    }

    fun check(assertions: KBoolExprs, getSolver: () -> KSolver<*>): Result {
        val cacheTimeout = this.cacheTimeout
        val checkResult = runBlocking {
            if (cacheTimeout == null) {
                cache.check(assertions)
            } else {
                withTimeoutOrNull(cacheTimeout) {
                    cache.check(assertions)
                }
            }
        }
        if (checkResult == true) {
            return CachedUnsat
        }

        return getSolver().use { solver ->
            assertions.forEach { solver.assertAndTrack(it) }
            val result = solver.check(solverTimeout)
            when (result) {
                KSolverStatus.SAT -> SolverSatResult(solver.model())
                KSolverStatus.UNSAT -> {
                    val core = solver.unsatCore()
                    runBlocking {
                        cache.addUnsatCore(core)
                    }
                    SolverUnsatResult(core)
                }

                KSolverStatus.UNKNOWN -> SolverUnknownResult
            }
        }
    }

}