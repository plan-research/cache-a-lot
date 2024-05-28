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
import org.jetbrains.kotlinx.dataframe.api.concat
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
import kotlin.io.path.nameWithoutExtension
import kotlin.math.max
import kotlin.system.exitProcess

private val scriptContext = ScriptContext()

private class StatsVisitor {

    val variables = ConcurrentHashMap<String, Int>()

    fun <T : KSort> visitExpr(expr: KExpr<T>, excludedDecl: PersistentSet<String> = persistentSetOf()): Int =
        when (expr) {
            is KFunctionApp<*> -> if (expr.args.isEmpty() && expr.decl.name !in excludedDecl) {
                variables.compute(expr.decl.name) { _, old ->
                    old?.plus(1) ?: 1
                }
                1
            } else {
                (expr.args.map { visitExpr(it, excludedDecl) }.maxOrNull() ?: 0) + 1
            }

            is KApp<*, *> -> (expr.args.map { visitExpr(it, excludedDecl) }.maxOrNull() ?: 0) + 1

            is KQuantifier -> visitExpr(expr.body, excludedDecl.addAll(expr.bounds.map { it.name })) + 1

            is KArrayLambdaBase<*, *> -> visitExpr(
                expr.body,
                excludedDecl.addAll(expr.indexVarDeclarations.map { it.name })
            ) + 1

            else -> {
                println("LOL: $expr")
                exitProcess(1)
            }
        }
}

private data class StatsEntry(
    var cnt: Int = 0,
    var sat: Int = 0,
    var unsat: Int = 0,
    var unknown: Int = 0,

    val files: MutableList<String> = mutableListOf(),
    val results: MutableList<KSolverStatus> = mutableListOf(),

    val numberOfVariables: MutableList<Int> = mutableListOf(),
    val maxNumberOfReps: MutableList<Int> = mutableListOf(),
    val numberOfClauses: MutableList<Int> = mutableListOf(),
    val height: MutableList<Int> = mutableListOf(),

    val numberOfVariablesInUnsatCore: MutableList<Int> = mutableListOf(),
    val maxNumberOfRepsInUnsatCore: MutableList<Int> = mutableListOf(),
    val numberOfClausesInUnsatCore: MutableList<Int> = mutableListOf(),
    val heightOfUnsatCore: MutableList<Int> = mutableListOf(),
) {
    private fun <T> List<T>.filterUnsat(): List<T> =
        filterIndexed { index, _ -> results[index] == KSolverStatus.UNSAT }

    private fun generateString(pref: String, list: List<Int>): String = """
        ${pref}Min: ${list.minOrNull()}, ${pref}Max: ${list.maxOrNull()}, 
        ${pref}Mean: ${list.mean()}, ${pref}Med: ${list.medianOrNull()}
    """.trim()

    override fun toString() = """
        Cnt: $cnt, Sat: $sat, Unsat: $unsat, Unknown: $unknown,
        ${generateString("Var", numberOfVariables)},
        ${generateString("Reps", maxNumberOfReps)},
        ${generateString("Clauses", numberOfClauses)},
        ${generateString("Heights", height)},
        ${generateString("UCVar", numberOfVariablesInUnsatCore.filterUnsat())},
        ${generateString("UCReps", maxNumberOfRepsInUnsatCore.filterUnsat())},
        ${generateString("UCClauses", numberOfClausesInUnsatCore.filterUnsat())},
        ${generateString("UCHeights", heightOfUnsatCore.filterUnsat())},
    """.trimIndent()

    operator fun plusAssign(statsEntry: StatsEntry) {
        cnt += statsEntry.cnt
        sat += statsEntry.sat
        unsat += statsEntry.unsat
        unknown += statsEntry.unknown

        files += statsEntry.files
        results += statsEntry.results

        numberOfVariables += statsEntry.numberOfVariables
        maxNumberOfReps += statsEntry.maxNumberOfReps
        numberOfClauses += statsEntry.numberOfClauses
        height += statsEntry.height

        numberOfVariablesInUnsatCore += statsEntry.numberOfVariablesInUnsatCore
        maxNumberOfRepsInUnsatCore += statsEntry.maxNumberOfRepsInUnsatCore
        numberOfClausesInUnsatCore += statsEntry.numberOfClausesInUnsatCore
        heightOfUnsatCore += statsEntry.heightOfUnsatCore
    }
}

private class StatsProcessor {

    private val cnt = AtomicInteger()
    private var sat = AtomicInteger()
    private var unsat = AtomicInteger()
    private var unknown = AtomicInteger()

    private val files = mutableListOf<String>()
    private val results = mutableListOf<KSolverStatus>()

    private val numberOfVariables = mutableListOf<Int>()
    private val maxNumberOfReps = mutableListOf<Int>()
    private val numberOfClauses = mutableListOf<Int>()
    private val height = mutableListOf<Int>()

    private val numberOfVariablesInUnsatCore = mutableListOf<Int>()
    private val maxNumberOfRepsInUnsatCore = mutableListOf<Int>()
    private val numberOfClausesInUnsatCore = mutableListOf<Int>()
    private val heightOfUnsatCore = mutableListOf<Int>()

    private var mutex = Mutex()

    val result: StatsEntry
        get() = StatsEntry(
            cnt = cnt.get(),
            sat = sat.get(),
            unsat = unsat.get(),
            unknown = unknown.get(),
            files = files,
            results = results,
            numberOfVariables = numberOfVariables,
            maxNumberOfReps = maxNumberOfReps,
            numberOfClauses = numberOfClauses,
            height = height,
            numberOfVariablesInUnsatCore = numberOfVariablesInUnsatCore,
            maxNumberOfRepsInUnsatCore = maxNumberOfRepsInUnsatCore,
            numberOfClausesInUnsatCore = numberOfClausesInUnsatCore,
            heightOfUnsatCore = heightOfUnsatCore,
        )

    suspend fun updateWith(path: Path) = with(scriptContext) {
        try {
            val assertions = parser.parse(path)

            cnt.incrementAndGet()
            val statsVisitor = StatsVisitor()
            val heightFormulae = AtomicInteger()
            coroutineScope {
                assertions.forEach {
                    launch {
                        val r = statsVisitor.visitExpr(it)
                        heightFormulae.getAndUpdate { max(r, it) }
                    }
                }
            }
            var clauses = 0
            var unsatCoreVariablesSize = 0
            var unsatCoreSize = 0
            var unsatCoreReps = 0
            var heightCore = AtomicInteger()

            val result = portfolioSolverManager.createPortfolioSolver(ctx).use { solver ->
                solver.configureAsync {
                    setIntParameter("random_seed", seed)
                    setIntParameter("seed", seed)
                }

                assertions.forEach { assertion ->
                    when (assertion) {
                        is KAndExpr -> assertion.args.forEach {
                            clauses += 1
                            solver.assertAndTrackAsync(it)
                        }

                        else -> solver.assertAndTrackAsync(assertion).also { clauses += 1 }
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
                                launch {
                                    val r = ucStatsVisitor.visitExpr(it)
                                    heightCore.getAndUpdate { max(r, it) }
                                }
                            }
                        }
                        unsatCoreVariablesSize = ucStatsVisitor.variables.size
                        unsatCoreSize = unsatCore.size
                        unsatCoreReps = ucStatsVisitor.variables.values.maxOrNull() ?: 0
                    }

                    KSolverStatus.UNKNOWN -> unknown.incrementAndGet()
                }
                result
            }

            mutex.withLock {
                files.add(path.nameWithoutExtension)
                results.add(result)

                numberOfVariables.add(statsVisitor.variables.size)
                maxNumberOfReps.add(statsVisitor.variables.values.maxOrNull() ?: 0)
                numberOfClauses.add(clauses)
                height.add(heightFormulae.get())

                numberOfVariablesInUnsatCore.add(unsatCoreVariablesSize)
                maxNumberOfRepsInUnsatCore.add(unsatCoreReps)
                numberOfClausesInUnsatCore.add(unsatCoreSize)
                heightOfUnsatCore.add(heightCore.get())
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
    var fullSmtData = dataFrameOf(
        "benchmark" to emptyList<String>(),
        "file" to emptyList<String>(),
        "result" to emptyList<String>(),
        "numberOfVariables" to emptyList<Int>(),
        "reps" to emptyList<Int>(),
        "numberOfClauses" to emptyList<Int>(),
        "height" to emptyList<Int>(),
        "ucNumberOfVariables" to emptyList<Int>(),
        "ucReps" to emptyList<Int>(),
        "ucNumberOfClauses" to emptyList<Int>(),
        "ucHeight" to emptyList<Int>(),
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
        fullSmtData = fullSmtData.concat(
            dataFrameOf(
                "benchmark" to List(result.files.size) { benchName },
                "file" to result.files,
                "result" to result.results.map { it.toString() },
                "numberOfVariables" to result.numberOfVariables,
                "reps" to result.maxNumberOfReps,
                "numberOfClauses" to result.numberOfClauses,
                "height" to result.height,
                "ucNumberOfVariables" to result.numberOfVariablesInUnsatCore,
                "ucReps" to result.maxNumberOfRepsInUnsatCore,
                "ucNumberOfClauses" to result.numberOfClausesInUnsatCore,
                "ucHeight" to result.heightOfUnsatCore,
            )
        )

        globalStats += result

        println(benchName + ":")
        println(result)
        println()
    }.onEnd {
        println("Global stats:")
        println(globalStats)

        smtData.writeCSV(outputPath.div("smtData.csv").toFile())
        fullSmtData.writeCSV(outputPath.div("fullSmtData.csv").toFile())
    }.execute(ExecutionMode.SMT_PARALLEL, Dispatchers.Default, path)

}