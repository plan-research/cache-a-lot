package org.plan.research.cachealot.scripts.cache

import com.jetbrains.rd.util.AtomicInteger
import io.ksmt.expr.KAndExpr
import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checker.KUnsatChecker
import org.plan.research.cachealot.scripts.BenchmarkExecutor
import org.plan.research.cachealot.scripts.ExecutionMode
import org.plan.research.cachealot.scripts.ScriptContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div

private val scriptContext = ScriptContext()
private val executionMode = ExecutionMode.BENCH_PARALLEL
private val coroutineScope = Dispatchers.Default

private val emptyChecker: KUnsatChecker
    get() = object : KUnsatChecker {
        override suspend fun addUnsatCore(unsatCore: KBoolExprs) {}
        override suspend fun check(exprs: KBoolExprs): Boolean = false
    }

private fun buildUnsatChecker(): KUnsatChecker {
    return emptyChecker
}

private data class StatsEntry(
    var cnt: Int = 0,
    var sat: Int = 0,
    var unsat: Int = 0,
    var unknown: Int = 0,
    var reusedUnsat: Int = 0,
) {
    operator fun plusAssign(other: StatsEntry) {
        this.cnt += other.cnt
        this.sat += other.sat
        this.unsat += other.unsat
        this.unknown += other.unknown
        this.reusedUnsat += other.reusedUnsat
    }


    override fun toString(): String {
        val percentage = if (unsat != 0) 100.0 * reusedUnsat / unsat else 0.0
        return """
            cnt = $cnt, sat = $sat,
            unsat = $unsat, unknown = $unknown,
            reusedUnsat = $reusedUnsat (${String.format("%.2f", percentage)}%)
        """.trimIndent()
    }
}

private class StatsCollector {
    private val cnt = AtomicInteger()
    private val sat = AtomicInteger()
    private val unsat = AtomicInteger()
    private val unknown = AtomicInteger()
    private val reusedUnsat = AtomicInteger()

    val result: StatsEntry
        get() = StatsEntry(cnt.get(), sat.get(), unsat.get(), unknown.get(), reusedUnsat.get())

    suspend fun update(unsatChecker: KUnsatChecker, path: Path) = with(scriptContext) {
        try {
            val assertions = parser.parse(path).flatMap {
                when (it) {
                    is KAndExpr -> it.args
                    else -> listOf(it)
                }
            }

            cnt.incrementAndGet()

            if (unsatChecker.check(assertions)) {
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
                val result = solver.checkAsync(timeout)
                when (result) {
                    KSolverStatus.SAT -> sat.incrementAndGet()

                    KSolverStatus.UNSAT -> {
                        unsat.incrementAndGet()
                        val unsatCore = solver.unsatCoreAsync()
                        unsatChecker.addUnsatCore(unsatCore)
                    }

                    KSolverStatus.UNKNOWN -> unknown.incrementAndGet()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun update(entry: StatsEntry) {
        cnt.addAndGet(entry.cnt)
        sat.addAndGet(entry.sat)
        unsat.addAndGet(entry.unsat)
        unknown.addAndGet(entry.unknown)
        reusedUnsat.addAndGet(entry.reusedUnsat)
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
    )
    val mutex = Mutex()

    BenchmarkExecutor {
        object {
            val localStats = StatsCollector()
            val unsatChecker = buildUnsatChecker()
        }
    }.onNewSmtFile {
        localStats.update(unsatChecker, it)
    }.onBenchmarkEnd { benchName ->
        val result = localStats.result

        mutex.withLock {
            globalStats += result
            smtStats = smtStats.append(
                benchName, result.cnt, result.sat, result.unsat,
                result.unknown, result.reusedUnsat
            )
            println("$benchName results:")
            println(result)
            println()
        }
    }.onEnd {
        println("Global results:")
        println(globalStats)
        println()

        smtStats.writeCSV(outputPath.div("smtData.csv").toFile())
    }.execute(executionMode, coroutineScope, path)

}
