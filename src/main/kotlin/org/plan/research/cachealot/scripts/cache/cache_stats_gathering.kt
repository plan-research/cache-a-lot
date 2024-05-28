package org.plan.research.cachealot.scripts.cache

import com.jetbrains.rd.util.printlnError
import io.ksmt.expr.KAndExpr
import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checker.KUnsatChecker
import org.plan.research.cachealot.checker.KUnsatCheckerFactory
import org.plan.research.cachealot.index.flat.KListIndex
import org.plan.research.cachealot.index.flat.KRandomIndex
import org.plan.research.cachealot.index.logging.withCandidatesNumberLog
import org.plan.research.cachealot.scripts.BenchmarkExecutor
import org.plan.research.cachealot.scripts.ExecutionMode
import org.plan.research.cachealot.scripts.ScriptContext
import org.plan.research.cachealot.scripts.scriptLogger
import org.plan.research.cachealot.statLogger
import org.plan.research.cachealot.testers.KFullOptTester
import org.plan.research.cachealot.testers.KFullTester
import org.plan.research.cachealot.testers.KSimpleTester
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.nameWithoutExtension
import kotlin.system.exitProcess
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val scriptContext = ScriptContext()

private val benchmarkPermits = scriptContext.poolSize
private val executionMode = ExecutionMode.BENCH_PARALLEL
private val coroutineScope = Dispatchers.Default
//private val executionMode = ExecutionMode.NONE_PARALLEL
//private val coroutineScope = EmptyCoroutineContext

private fun buildUnsatChecker(name: String): KUnsatChecker {
//    return KUnsatCheckerFactory.create()
    return KUnsatCheckerFactory.create(
        KFullOptTester(scriptContext.ctx),
//        KFullTester(scriptContext.ctx),
//        KSimpleTester(),
//        KListIndex<KBoolExprs>()
        KRandomIndex<KBoolExprs>(10)
            .withCandidatesNumberLog("$name index")
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

private class StatsCollector(private val name: String) {
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
        val fullName = "$name ${path.nameWithoutExtension}"
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
            statLogger.info {
                "$fullName check: $checkResult, ${checkingDuration.inWholeMilliseconds}"
            }
            checkingTime.addAndGet(checkingDuration.inWholeMilliseconds)
            if (checkResult) {
                unsat.incrementAndGet()
                reusedUnsat.incrementAndGet()
                return@with
            }

            if (executionMode == ExecutionMode.NONE_PARALLEL) {
                z3Solver.use { solver ->
                    solver.configure {
                        setIntParameter("random_seed", seed)
                        setIntParameter("seed", seed)
                    }

                    assertions.forEach { solver.assertAndTrack(it) }
                    val (result, solvingDuration) = measureTimedValue {
                        solver.check(timeout)
                    }
                    statLogger.info {
                        "$fullName solve: $result, ${solvingDuration.inWholeMilliseconds}"
                    }
                    solvingTime.addAndGet(solvingDuration.inWholeMilliseconds)
                    when (result) {
                        KSolverStatus.SAT -> sat.incrementAndGet()

                        KSolverStatus.UNSAT -> {
                            unsat.incrementAndGet()
                            val updatingDuration = measureTime {
                                val unsatCore = solver.unsatCore()
                                unsatChecker.addUnsatCore(unsatCore)
                            }
                            statLogger.info {
                                "$fullName update: ${updatingDuration.inWholeMilliseconds}"
                            }
                            updatingTime.addAndGet(updatingDuration.inWholeMilliseconds)
                        }

                        KSolverStatus.UNKNOWN -> unknown.incrementAndGet()
                    }
                }
            } else {
                portfolioSolverManager.createPortfolioSolver(ctx).use { solver ->
                    solver.configureAsync {
                        setIntParameter("random_seed", seed)
                        setIntParameter("seed", seed)
                    }

                    assertions.forEach { solver.assertAndTrackAsync(it) }
                    val (result, solvingDuration) = measureTimedValue {
                        solver.checkAsync(timeout)
                    }
                    statLogger.info {
                        "$fullName solve: $result, ${solvingDuration.inWholeMilliseconds}"
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
                            statLogger.info {
                                "$fullName update: ${updatingDuration.inWholeMilliseconds}"
                            }
                            updatingTime.addAndGet(updatingDuration.inWholeMilliseconds)
                        }

                        KSolverStatus.UNKNOWN -> unknown.incrementAndGet()
                    }
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
            val localStats = StatsCollector(it)
            val unsatChecker = buildUnsatChecker(it)
        }
    }.onNewBenchmark {
        benchSemaphore.acquire()
        scriptLogger.debug { "New Benchmark: $it" }
        true
    }.onNewSmtFile {
        scriptLogger.debug { "New Smt File: $it" }
        localStats.update(unsatChecker, it)
    }.onBenchmarkEnd { benchName ->
        scriptLogger.debug { "Benchmark ended: $benchName" }
        val result = localStats.result

        mutex.withLock {
            globalStats += result
            smtStats = smtStats.append(
                benchName, result.cnt, result.sat, result.unsat,
                result.unknown, result.reusedUnsat, result.solvingTime,
                result.checkingTime, result.updatingTime,
            )
            scriptLogger.info {
                "$benchName results:\n" + "$result"
            }
        }
        benchSemaphore.release()
    }.onEnd {
        scriptLogger.info {
            "Global results:\n" + "$globalStats"
        }

        smtStats.writeCSV(outputPath.div("smtData.csv").toFile())
    }.execute(executionMode, coroutineScope, path)
}
