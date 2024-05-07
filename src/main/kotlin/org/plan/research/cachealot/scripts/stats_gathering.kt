package org.plan.research.cachealot.scripts

import com.jetbrains.rd.util.ConcurrentHashMap
import com.microsoft.z3.Context
import io.ksmt.KContext
import io.ksmt.expr.*
import io.ksmt.expr.rewrite.simplify.KExprSimplifier
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.portfolio.KPortfolioSolverManager
import io.ksmt.solver.z3.KZ3Context
import io.ksmt.solver.z3.KZ3ExprConverter
import io.ksmt.solver.z3.KZ3SMTLibParser
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSort
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.jetbrains.kotlinx.dataframe.math.mean
import org.jetbrains.kotlinx.dataframe.math.medianOrNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val ctx = KContext(simplificationMode = KContext.SimplificationMode.NO_SIMPLIFY)
private val parser = KZ3SMTLibParser(ctx)
private val timeout = 5.seconds
private const val seed = 42
private val portfolioSolverManager: KPortfolioSolverManager =
    KPortfolioSolverManager(
        solvers = listOf(KZ3Solver::class),
        portfolioPoolSize = 8,
        hardTimeout = timeout * 2,
        workerProcessIdleTimeout = 10.seconds,
    )

private fun parseAndSimplifyByZ3(path: Path): List<KExpr<KBoolSort>> =
    KZ3Context(ctx).use { z3Ctx ->
        val result = z3Ctx.nativeContext.parseSMTLIB2File(
            path.absolutePathString(),
            emptyArray(),
            emptyArray(),
            emptyArray(),
            emptyArray()
        )
        val goal = z3Ctx.nativeContext.mkGoal(true, true, false)
        goal.add(*result)
        val converter = KZ3ExprConverter(ctx, z3Ctx)
        with(converter) {
            goal.simplify().formulas.map {
                z3Ctx.nativeContext.unwrapAST(it).convertExpr()
            }
        }
    }

class StatsVisitor {

    val variables = ConcurrentHashMap<String, Unit>()

    fun <T : KSort> visitExpr(expr: KExpr<T>, excludedDecl: PersistentSet<String> = persistentSetOf()) {
        when (expr) {
            is KFunctionApp<*> ->
                if (expr.args.isEmpty() && expr.decl.name !in excludedDecl) variables[expr.decl.name] = Unit

            is KApp<*, *> ->
                expr.args.forEach { visitExpr(it, excludedDecl) }

            is KQuantifier ->
                visitExpr(expr.body, excludedDecl.addAll(expr.bounds.map { it.name }))

            is KArrayLambdaBase<*, *> ->
                visitExpr(expr.body, excludedDecl.addAll(expr.indexVarDeclarations.map { it.name }))

            else -> {
                println("LOL: $expr")
                exitProcess(1)
            }
        }
    }
}

data class StatsEntry(
    val cnt: Int,
    val sat: Int,
    val unsat: Int,
    val unknown: Int,
    val numberOfVariables: List<Int>,
    val numberOfVariablesInUnsatCore: List<Int>,
    val numberOfClausesInUnsatCore: List<Int>,
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
}

class StatsProcessor {

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

    suspend fun updateWith(path: Path) {
        try {
//            val assertions = parseAndSimplifyByZ3(path)
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

    suspend fun updateWith(stats: StatsEntry) {
        cnt.addAndGet(stats.cnt)
        sat.addAndGet(stats.sat)
        unsat.addAndGet(stats.unsat)
        unknown.addAndGet(stats.unknown)
        mutex.withLock {
            numberOfVariables += stats.numberOfVariables
        }
        mutex2.withLock {
            numberOfVariablesInUnsatCore += stats.numberOfVariablesInUnsatCore
            numberOfClausesInUnsatCore += stats.numberOfClausesInUnsatCore
        }
    }
}


fun main(args: Array<String>) {
    KZ3Solver(KContext()).close()

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

    val globalStatsProcessor = StatsProcessor()

    path.forEachDirectoryEntry { folder ->
        runBlocking(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            e.printStackTrace()
            exitProcess(1)
        }) {
            val statsProcessor = StatsProcessor()
            coroutineScope {
                folder.forEachDirectoryEntry { path ->
                    launch {
                        statsProcessor.updateWith(path)
                    }
                }
            }

            val result = statsProcessor.result
            smtData = smtData.append(folder.name, result.cnt, result.sat, result.unsat, result.unknown)
            result.numberOfVariables.forEach {
                variablesDistribution = variablesDistribution.append(folder.name, it)
            }
            result.numberOfVariablesInUnsatCore.forEach {
                ucVariablesDistribution = ucVariablesDistribution.append(folder.name, it)
            }
            result.numberOfClausesInUnsatCore.forEach {
                ucClausesDistribution = ucClausesDistribution.append(folder.name, it)
            }


            globalStatsProcessor.updateWith(result)

            println(folder.name + ":")
            println(result)
            println()
        }
    }

    val result = globalStatsProcessor.result
    println("Global stats:")
    println(result)

    smtData.writeCSV(outputPath.div("smtData.csv").toFile())
    variablesDistribution.writeCSV(outputPath.div("variablesDistribution.csv").toFile())
    ucVariablesDistribution.writeCSV(outputPath.div("ucVariablesDistribution.csv").toFile())
    ucClausesDistribution.writeCSV(outputPath.div("ucClausesDistribution.csv").toFile())
}
