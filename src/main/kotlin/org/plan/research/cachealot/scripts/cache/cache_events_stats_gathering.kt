package org.plan.research.cachealot.scripts.cache

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.jetbrains.kotlinx.dataframe.math.mean
import org.jetbrains.kotlinx.dataframe.math.medianOrNull
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.properties.Delegates
import kotlin.time.TimedValue


class CacheEventSubstriber : LinearStatsParserSubscriber {

    val benchmark = mutableListOf<String>()
    val smt = mutableListOf<String>()
    val event = mutableListOf<String?>()
    val duration = mutableListOf<Long>()
    val result = mutableListOf<String?>()

    private fun updateName(name: String) {
        val benchName = name.substringBefore(' ')
        val smtName = name.substringAfter(' ', "").takeIf { it.isNotBlank() } ?: smt.last()
        benchmark += benchName
        smt += smtName
    }

    private fun <T> update(parseResult: ParseResult<TimedValue<T>>) {
        updateName(parseResult.name)
        val (value, duration) = parseResult.value
        this.duration += duration.inWholeMilliseconds
        result += value.toString()
    }

    override suspend fun onCheck(parseResult: ParseResultCheck) {
        event += "check"
        update(parseResult)
    }

    override suspend fun onSolve(parseResult: ParseResultSolve) {
        event += "solve"
        update(parseResult)
    }

    override suspend fun onUpdate(parseResult: ParseResultUpdate) {
        event += "update"
        updateName(parseResult.name)
        duration += parseResult.value.inWholeMilliseconds
        result += null
    }

    override suspend fun onCandidatesConsumed(parseResult: ParseResultCandidatesConsumed) {}
}

fun compute(path: Path) {
    val subscriber = CacheEventSubstriber()
    runBlocking {
        LinearStatsParser(subscriber).run(path.div("stats.log"))
    }
    subscriber.run {
        dataFrameOf(
            "benchmark" to benchmark,
            "smt" to smt,
            "event" to event,
            "duration" to duration,
            "result" to result,
        )
    }.writeCSV(path.div("stats.csv").toFile())
}

fun main(args: Array<String>) {
    val path = Path(args[0])

    if (path.div("stats.log").exists()) {
        compute(path)
    } else {
        path.forEachDirectoryEntry {
            compute(it)
        }
    }
}