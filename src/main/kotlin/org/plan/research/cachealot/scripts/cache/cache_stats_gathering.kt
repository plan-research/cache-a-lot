package org.plan.research.cachealot.scripts.cache

import com.jetbrains.rd.util.AtomicInteger
import io.ksmt.expr.KAndExpr
import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.plan.research.cachealot.checker.KUnsatChecker
import org.plan.research.cachealot.checker.KUnsatCheckerFactory
import org.plan.research.cachealot.index.flat.KRandomIndex
import org.plan.research.cachealot.scripts.BenchmarkExecutor
import org.plan.research.cachealot.scripts.ExecutionMode
import org.plan.research.cachealot.scripts.ScriptContext
import org.plan.research.cachealot.testers.KSimpleTester
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val scriptContext = ScriptContext()
private val executionMode = ExecutionMode.BENCH_PARALLEL
private val coroutineScope = Dispatchers.Default
private val benchmarkPermits = scriptContext.poolSize

private fun buildUnsatChecker(): KUnsatChecker {
//    return KUnsatCheckerFactory.create()
    return KUnsatCheckerFactory.create(
        KSimpleTester(),
        KRandomIndex(10)
    )
}

private data class StatsEntry(
    var cnt: Int = 0,
    var sat: Int = 0,
    var unsat: Int = 0,
    var unknown: Int = 0,
    var reusedUnsat: Int = 0,
    var solvingTime: Long = 0,
    var checkingTime: Long = 0,
    var updatingTime: Long = 0,
) {
    operator fun plusAssign(other: StatsEntry) {
        this.cnt += other.cnt
        this.sat += other.sat
        this.unsat += other.unsat
        this.unknown += other.unknown
        this.reusedUnsat += other.reusedUnsat
        this.solvingTime += other.solvingTime
        this.checkingTime += other.checkingTime
        this.updatingTime += other.updatingTime
    }

    override fun toString(): String {
        val percentage = if (unsat != 0) 100.0 * reusedUnsat / unsat else 0.0
        return """
            cnt = $cnt, sat = $sat,
            unsat = $unsat, unknown = $unknown,
            reusedUnsat = $reusedUnsat (${String.format("%.2f", percentage)}%),
            solvingTime = $solvingTime ms, checkingTime = $checkingTime ms,
            updatingTime = $updatingTime ms, totalTime = ${solvingTime + checkingTime + updatingTime} ms
        """.trimIndent()
    }
}

private class StatsCollector {
    private val cnt = AtomicInteger()
    private val sat = AtomicInteger()
    private val unsat = AtomicInteger()
    private val unknown = AtomicInteger()
    private val reusedUnsat = AtomicInteger()
    private val solvingTime = AtomicLong()
    private val checkingTime = AtomicLong()
    private val updatingTime = AtomicLong()

    val result: StatsEntry
        get() = StatsEntry(
            cnt.get(),
            sat.get(),
            unsat.get(),
            unknown.get(),
            reusedUnsat.get(),
            solvingTime.get(),
            checkingTime.get(),
            updatingTime.get(),
        )

    suspend fun update(unsatChecker: KUnsatChecker, path: Path) = with(scriptContext) {
        try {
            val assertions = parser.parse(path).flatMap {
                when (it) {
                    is KAndExpr -> it.args
                    else -> listOf(it)
                }
            }

            cnt.incrementAndGet()

            val (checkResult, checkingDuration) = measureTimedValue {
                unsatChecker.check(assertions)
            }
            checkingTime.addAndGet(checkingDuration.inWholeMilliseconds)
            if (checkResult) {
                unsat.incrementAndGet()
                reusedUnsat.incrementAndGet()
                return@with
            }

            portfolioSolverManager.createPortfolioSolver(ctx).use { solver ->
                solver.configureAsync {
                    setIntParameter("random_seed", seed)
                    setIntParameter("seed", seed)
                }

                assertions.forEach { solver.assertAndTrackAsync(it) }
                val (result, solvingDuration) = measureTimedValue {
                    solver.checkAsync(timeout)
                }
                solvingTime.addAndGet(solvingDuration.inWholeMilliseconds)
                when (result) {
                    KSolverStatus.SAT -> sat.incrementAndGet()

                    KSolverStatus.UNSAT -> {
                        unsat.incrementAndGet()
                        val updatingDuration = measureTime {
                            val unsatCore = solver.unsatCoreAsync()
                            unsatChecker.addUnsatCore(unsatCore)
                        }
                        updatingTime.addAndGet(updatingDuration.inWholeMilliseconds)
                    }

                    KSolverStatus.UNKNOWN -> unknown.incrementAndGet()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun main(args: Array<String>) {
    val path = Path(args[0])
    val outputPath = Path(args[1]).also {
        it.createDirectory()
    }

    val globalStats = StatsEntry()
    var smtStats = dataFrameOf(
        "benchmark" to emptyList<String>(),
        "cnt" to emptyList<Int>(),
        "sat" to emptyList<Int>(),
        "unsat" to emptyList<Int>(),
        "unknown" to emptyList<Int>(),
        "reusedUnsat" to emptyList<Int>(),
        "solvingTime" to emptyList<Long>(),
        "checkingTime" to emptyList<Long>(),
        "updatingTime" to emptyList<Long>(),
    )
    val mutex = Mutex()
    val benchSemaphore = Semaphore(benchmarkPermits)

    BenchmarkExecutor {
        object {
            val localStats = StatsCollector()
            val unsatChecker = buildUnsatChecker()
        }
    }.onNewBenchmark {
        benchSemaphore.acquire()
        true
    }.onNewSmtFile {
        localStats.update(unsatChecker, it)
    }.onBenchmarkEnd { benchName ->
        val result = localStats.result

        mutex.withLock {
            globalStats += result
            smtStats = smtStats.append(
                benchName, result.cnt, result.sat, result.unsat,
                result.unknown, result.reusedUnsat, result.solvingTime,
                result.checkingTime, result.updatingTime,
            )
            println("$benchName results:")
            println(result)
            println()
        }
        benchSemaphore.release()
    }.onEnd {
        println("Global results:")
        println(globalStats)
        println()

        smtStats.writeCSV(outputPath.div("smtData.csv").toFile())
    }.execute(executionMode, coroutineScope, path)

}
