package org.plan.research.cachealot.scripts.cache

import io.ksmt.expr.KAndExpr
import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.cache.KUnsatCacheFactory
import org.plan.research.cachealot.cache.decorators.filters.withUnsatCoreMaxSize
import org.plan.research.cachealot.cache.decorators.onCheckEnd
import org.plan.research.cachealot.hash.KCacheContext
import org.plan.research.cachealot.index.bloom.KBloomFilterIndex
import org.plan.research.cachealot.index.flat.KFlatIndex
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val scriptContext = ScriptContext()

private val benchmarkPermits = scriptContext.poolSize

//private val executionMode = ExecutionMode.BENCH_PARALLEL
//private val coroutineScope = Dispatchers.Default
//private val usePortfolio = true
private val executionMode = ExecutionMode.NONE_PARALLEL
private val coroutineScope = EmptyCoroutineContext
private val usePortfolio = false

private class ScriptProperties(path: Path) : CacheScriptPropertiesBase(path) {
    val empty = getProperty<Boolean>("cache", "empty") ?: false
    val tester = getStringProperty("cache", "tester")
    val index = getStringProperty("cache", "index")
    val nbits = getProperty<Int>("cache", "nbits")
    val exclude = getListStringProperty("cache", "exclude")
    val maxUnsatCoreSize = getProperty<Int>("cache", "maxUnsatCoreSize")
}

private fun buildUnsatCache(properties: ScriptProperties, name: String): KUnsatCache {
    if (properties.empty) {
        return KUnsatCacheFactory.create()
    }

    val cacheContext = KCacheContext()
    val tester = when (properties.tester) {
        "simple" -> KSimpleTester()
        "full" -> KFullTester(scriptContext.ctx)
        "fullopt" -> KFullOptTester(scriptContext.ctx, cacheContext)
        else -> throw IllegalArgumentException("Unsupported tester class: ${properties.tester}")
    }

    val index = when (properties.index) {
        "random" -> KRandomIndex(10)
        "list" -> KListIndex()
        "bloom" -> KBloomFilterIndex(properties.nbits!!, cacheContext.exprHasher, cacheContext.exprHasher)
        else -> throw IllegalArgumentException("Unsupported index: ${properties.index}")
    }

    var cache = if (index is KFlatIndex) {
        KUnsatCacheFactory.create(tester, index.withCandidatesNumberLog("$name index"))
    } else {
        KUnsatCacheFactory.create(tester, index.withCandidatesNumberLog("$name index"))
    }.onCheckEnd { cacheContext.clearExprsRelated() }

    properties.maxUnsatCoreSize?.let {
        cache = cache.withUnsatCoreMaxSize(it)
    }

    return cache
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

    suspend fun update(unsatCache: KUnsatCache, path: Path) = with(scriptContext) {
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
                withTimeoutOrNull(1.seconds) {
                    unsatCache.check(assertions)
                }
            }
            statLogger.info {
                "$fullName check: $checkResult, ${checkingDuration.inWholeMilliseconds}"
            }
            checkingTime.addAndGet(checkingDuration.inWholeMilliseconds)
            if (checkResult == true) {
                unsat.incrementAndGet()
                reusedUnsat.incrementAndGet()
                return@with
            }

            if (!usePortfolio) {
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
                                unsatCache.addUnsatCore(unsatCore)
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
                                unsatCache.addUnsatCore(unsatCore)
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
    val properties = ScriptProperties(Path(args[0]))
    val path = Path(args[1])
    val outputPath = Path(args[2]).also {
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
            val unsatCache = buildUnsatCache(properties, it)
        }
    }.onNewBenchmark {
        if (properties.exclude.any { e -> e in it }) return@onNewBenchmark false
        benchSemaphore.acquire()
        scriptLogger.debug { "New Benchmark: $it" }
        true
    }.onNewSmtFile {
        scriptLogger.debug { "New Smt File: $it" }
        localStats.update(unsatCache, it)
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
