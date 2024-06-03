package org.plan.research.cachealot.scripts.cache

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.dataframe.math.mean
import org.jetbrains.kotlinx.dataframe.math.medianOrNull
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.div

class CacheEventSubscriberEntry(val name: String) {
    val candidates = mutableListOf<Int>()
    val satTime = mutableListOf<Long>()
    val unsatTime = mutableListOf<Long>()
    val unknownTime = mutableListOf<Long>()
    val trueCheckTime = mutableListOf<Long>()
    val falseCheckTime = mutableListOf<Long>()

    fun add(other: CacheEventSubscriberEntry) {
        candidates.addAll(other.candidates)
        satTime.addAll(other.satTime)
        unsatTime.addAll(other.unsatTime)
        unknownTime.addAll(other.unknownTime)
        trueCheckTime.addAll(other.trueCheckTime)
        falseCheckTime.addAll(other.falseCheckTime)
    }

    private fun generateString(pref: String, list: List<Int>): String = """
        ${pref}Min: ${list.minOrNull()}, ${pref}Max: ${list.maxOrNull()}, 
        ${pref}Mean: ${list.mean()}, ${pref}Med: ${list.medianOrNull()},
        ${pref}Sum: ${list.sum()}
    """.trim()

    private fun generateString(pref: String, list: Collection<Long>): String = """
        ${pref}Min: ${list.minOrNull()}, ${pref}Max: ${list.maxOrNull()}, 
        ${pref}Mean: ${list.mean()}, ${pref}Med: ${list.medianOrNull()},
        ${pref}Sum: ${list.sum()}
    """.trim()

    override fun toString(): String = """
        $name:
        
        ${generateString("candidates", candidates)},
        
        ${generateString("satTime", satTime)},
        
        ${generateString("unsatTime", unsatTime)},
        
        ${generateString("unknownTime", unknownTime)},
        
        ${generateString("successSolveTime", satTime + unsatTime)},
        
        ${generateString("solveTime", satTime + unsatTime + unknownTime)},
        
        ${generateString("trueCheckTime", trueCheckTime)},
        
        ${generateString("falseCheckTime", falseCheckTime)},
        
        ${generateString("checkTime", trueCheckTime + falseCheckTime)},
    """.trimIndent()
}

class CacheEventSubstriber : LinearStatsParserSubscriber {

    val projects = hashMapOf<String, CacheEventSubscriberEntry>()

    private inline fun withBench(name: String, block: CacheEventSubscriberEntry.() -> Unit) {
        val bench = name.substringBefore('-')
        projects.getOrPut(bench) { CacheEventSubscriberEntry(bench) }.block()
    }

    override suspend fun onCheck(parseResult: ParseResultCheck) {
        withBench(parseResult.name) {
            val (value, duration) = parseResult.value
            if (value) {
                trueCheckTime += duration.inWholeMilliseconds
            } else {
                falseCheckTime += duration.inWholeMilliseconds
            }
        }
    }

    override suspend fun onSolve(parseResult: ParseResultSolve) {
        withBench(parseResult.name) {
            val (value, duration) = parseResult.value
            when (value) {
                KSolverStatus.SAT -> satTime += duration.inWholeMilliseconds
                KSolverStatus.UNSAT -> unsatTime += duration.inWholeMilliseconds
                KSolverStatus.UNKNOWN -> unknownTime += duration.inWholeMilliseconds
            }
        }
    }

    override suspend fun onUpdate(parseResult: ParseResultUpdate) {
    }

    override suspend fun onCandidatesConsumed(parseResult: ParseResultCandidatesConsumed) {
        withBench(parseResult.name) {
            candidates += parseResult.value
        }
    }

    override fun toString(): String = buildString {
        val all = CacheEventSubscriberEntry("ALL")
        projects.forEach { (_, value) ->
            appendLine(value)
            appendLine()
            all.add(value)
        }

        appendLine(all)
    }
}

fun main(args: Array<String>) {
    val path = Path(args[0]).div("stats.log")
    val subscriber = CacheEventSubstriber()
    runBlocking {
        LinearStatsParser(subscriber).run(path)
    }
    print(subscriber)
}