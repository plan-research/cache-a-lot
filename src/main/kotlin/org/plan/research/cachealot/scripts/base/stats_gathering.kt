package org.plan.research.cachealot.scripts.base

import com.jetbrains.rd.util.ConcurrentHashMap
import io.ksmt.expr.*
import io.ksmt.solver.KSolverStatus
import io.ksmt.sort.KSort
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.jetbrains.kotlinx.dataframe.math.mean
import org.jetbrains.kotlinx.dataframe.math.medianOrNull
import org.plan.research.cachealot.scripts.BenchmarkExecutor
import org.plan.research.cachealot.scripts.ExecutionMode
import org.plan.research.cachealot.scripts.ScriptContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.system.exitProcess

private val scriptContext = ScriptContext()

private class StatsVisitor {

    val variables = ConcurrentHashMap<String, Unit>()

    fun <T : KSort> visitExpr(expr: KExpr<T>, excludedDecl: PersistentSet<String> = persistentSetOf()) {
        when (expr) {
            is KFunctionApp<*> -> if (expr.args.isEmpty() && expr.decl.name !in excludedDecl) variables[expr.decl.name] =
                Unit

            is KApp<*, *> -> expr.args.forEach { visitExpr(it, excludedDecl) }

            is KQuantifier -> visitExpr(expr.body, excludedDecl.addAll(expr.bounds.map { it.name }))

            is KArrayLambdaBase<*, *> -> visitExpr(
                expr.body,
                excludedDecl.addAll(expr.indexVarDeclarations.map { it.name })
            )

            else -> {
                println("LOL: $expr")
                exitProcess(1)
            }
        }
    }
}

private data class StatsEntry(
    var cnt: Int = 0,
    var sat: Int = 0,
    var unsat: Int = 0,
    var unknown: Int = 0,
    val numberOfVariables: MutableList<Int> = mutableListOf(),
    val numberOfVariablesInUnsatCore: MutableList<Int> = mutableListOf(),
    val numberOfClausesInUnsatCore: MutableList<Int> = mutableListOf(),
) {
    private fun generateString(pref: String, list: List<Int>): String = """
        ${pref}Min: ${list.minOrNull()}, ${pref}Max: ${list.maxOrNull()}, 
        ${pref}Mean: ${list.mean()}, ${pref}Med: ${list.medianOrNull()}
    """.trim()

    override fun toString() = """
        Cnt: $cnt, Sat: $sat, Unsat: $unsat, Unknown: $unknown,
        ${generateString("Var", numberOfVariables)},
        ${generateString("UCVar", numberOfVariablesInUnsatCore)},
        ${generateString("UCClauses", numberOfClausesInUnsatCore)}
    """.trimIndent()

    operator fun plusAssign(statsEntry: StatsEntry) {
        cnt += statsEntry.cnt
        sat += statsEntry.sat
        unsat += statsEntry.unsat
        unknown += statsEntry.unknown
        numberOfVariables += statsEntry.numberOfVariables
        numberOfVariablesInUnsatCore += statsEntry.numberOfVariablesInUnsatCore
        numberOfClausesInUnsatCore += statsEntry.numberOfClausesInUnsatCore
    }
}

private class StatsProcessor {

    private val cnt = AtomicInteger()
    private var sat = AtomicInteger()
    private var unsat = AtomicInteger()
    private var unknown = AtomicInteger()
    private var numberOfVariables = mutableListOf<Int>()
    private val numberOfVariablesInUnsatCore = mutableListOf<Int>()
    private val numberOfClausesInUnsatCore = mutableListOf<Int>()
    private var mutex = Mutex()
    private var mutex2 = Mutex()

    val result: StatsEntry
        get() = StatsEntry(
            cnt.get(),
            sat.get(),
            unsat.get(),
            unknown.get(),
            numberOfVariables,
            numberOfVariablesInUnsatCore,
            numberOfClausesInUnsatCore
        )

    suspend fun updateWith(path: Path) = with(scriptContext) {
        try {
            val assertions = parser.parse(path)

            cnt.incrementAndGet()
            val statsVisitor = StatsVisitor()
            coroutineScope {
                assertions.forEach {
                    launch { statsVisitor.visitExpr(it) }
                }
            }
            mutex.withLock {
                numberOfVariables.add(statsVisitor.variables.size)
            }

            portfolioSolverManager.createPortfolioSolver(ctx).use { solver ->

                solver.configureAsync {
                    setIntParameter("random_seed", seed)
                    setIntParameter("seed", seed)
                }

                assertions.forEach { assertion ->
                    when (assertion) {
                        is KAndExpr -> assertion.args.forEach {
                            solver.assertAndTrackAsync(it)
                        }

                        else -> solver.assertAndTrackAsync(assertion)
                    }
                }
                val result = solver.checkAsync(timeout)
                when (result) {
                    KSolverStatus.SAT -> sat.incrementAndGet()
                    KSolverStatus.UNSAT -> {
                        unsat.incrementAndGet()
                        val unsatCore = solver.unsatCoreAsync()
                        val ucStatsVisitor = StatsVisitor()
                        coroutineScope {
                            unsatCore.forEach {
                                launch { ucStatsVisitor.visitExpr(it) }
                            }
                        }
                        mutex2.withLock {
                            numberOfVariablesInUnsatCore += ucStatsVisitor.variables.size
                            numberOfClausesInUnsatCore += unsatCore.size
                        }
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

    var smtData = dataFrameOf(
        "benchmark" to emptyList<String>(),
        "cnt" to emptyList<Int>(),
        "sat" to emptyList<Int>(),
        "unsat" to emptyList<Int>(),
        "unknown" to emptyList<Int>(),
    )
    var variablesDistribution = dataFrameOf(
        "benchmark" to emptyList<String>(),
        "numberOfVariables" to emptyList<Int>(),
    )
    var ucVariablesDistribution = dataFrameOf(
        "benchmark" to emptyList<String>(),
        "numberOfVariables" to emptyList<Int>(),
    )
    var ucClausesDistribution = dataFrameOf(
        "benchmark" to emptyList<String>(),
        "numberOfClauses" to emptyList<Int>(),
    )

    val globalStats = StatsEntry()

    BenchmarkExecutor {
        object {
            val localStatsProcessor = StatsProcessor()
        }
    }.onNewSmtFile {
        localStatsProcessor.updateWith(it)
    }.onBenchmarkEnd { benchName ->
        val result = localStatsProcessor.result
        smtData = smtData.append(benchName, result.cnt, result.sat, result.unsat, result.unknown)
        result.numberOfVariables.forEach {
            variablesDistribution = variablesDistribution.append(benchName, it)
        }
        result.numberOfVariablesInUnsatCore.forEach {
            ucVariablesDistribution = ucVariablesDistribution.append(benchName, it)
        }
        result.numberOfClausesInUnsatCore.forEach {
            ucClausesDistribution = ucClausesDistribution.append(benchName, it)
        }

        globalStats += result

        println(benchName + ":")
        println(result)
        println()
    }.onEnd {
        println("Global stats:")
        println(globalStats)

        smtData.writeCSV(outputPath.div("smtData.csv").toFile())
        variablesDistribution.writeCSV(outputPath.div("variablesDistribution.csv").toFile())
        ucVariablesDistribution.writeCSV(outputPath.div("ucVariablesDistribution.csv").toFile())
        ucClausesDistribution.writeCSV(outputPath.div("ucClausesDistribution.csv").toFile())
    }.execute(ExecutionMode.SMT_PARALLEL, Dispatchers.Default, path)

}