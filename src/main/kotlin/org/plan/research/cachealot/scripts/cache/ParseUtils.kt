package org.plan.research.cachealot.scripts.cache

import io.ksmt.solver.KSolverStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.forEachLine
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimedValue
import kotlin.time.toDuration


typealias ParseResultPure = ParseResult<String>
typealias ParseResultCheck = ParseResult<TimedValue<Boolean>>
typealias ParseResultSolve = ParseResult<TimedValue<KSolverStatus>>
typealias ParseResultUpdate = ParseResult<Duration>
typealias ParseResultCandidatesConsumed = ParseResult<Int>

data class ParseResult<T>(val name: String, val value: T)

fun parse(s: String): ParseResultPure {
    val name = s.substringBefore(":").trim()
    val value = s.substringAfter(":").trim()
    return ParseResult(name, value)
}

fun ParseResultPure.isCheck(): Boolean = name.endsWith("check")
fun ParseResultPure.asCheck(): ParseResultCheck =
    ParseResult(
        name.substringBefore(" check"),
        TimedValue(
            value.substringBefore(",").toBoolean(),
            value.substringAfter(",").trim().toLong().toDuration(DurationUnit.MILLISECONDS)
        )
    )

fun ParseResultPure.isSolve(): Boolean = name.endsWith("solve")
fun ParseResultPure.asSolve(): ParseResultSolve =
    ParseResult(
        name.substringBefore(" solve"),
        TimedValue(
            KSolverStatus.valueOf(value.substringBefore(",")),
            value.substringAfter(",").trim().toLong().toDuration(DurationUnit.MILLISECONDS)
        )
    )

fun ParseResultPure.isUpdate(): Boolean = name.endsWith("update")
fun ParseResultPure.asUpdate(): ParseResultUpdate =
    ParseResult(
        name.substringBefore(" update"),
        value.toLong().toDuration(DurationUnit.MILLISECONDS)
    )

fun ParseResultPure.isCandidatesConsumed(): Boolean = name.endsWith("index candidates consumed")
fun ParseResultPure.asCandidatesConsumed(): ParseResultCandidatesConsumed =
    ParseResult(
        name.substringBefore(" index candidates consumed"),
        value.toInt()
    )

interface LinearStatsParserSubscriber {
    suspend fun onCheck(parseResult: ParseResultCheck)
    suspend fun onSolve(parseResult: ParseResultSolve)
    suspend fun onUpdate(parseResult: ParseResultUpdate)
    suspend fun onCandidatesConsumed(parseResult: ParseResultCandidatesConsumed)
}

class LinearStatsParser(
    private val subscriber: LinearStatsParserSubscriber,
) {
    private enum class State {
        CANDIDATES_CONSUMED,
        CHECK,
        SOLVE,
        UPDATE,
    }

    private var withCandidatesState by Delegates.notNull<Boolean>()
    private val defaultState by lazy { if (withCandidatesState) State.CANDIDATES_CONSUMED else State.CHECK }
    private val currentBenchStates: ConcurrentHashMap<String, State> = ConcurrentHashMap()

    suspend fun consume(s: String) {
        val parseResult = parse(s)
        val bench = parseResult.name.substringBefore(" ")
        val state = currentBenchStates.getOrDefault(bench, defaultState)

        val newState: State = when (state) {
            State.CANDIDATES_CONSUMED -> {
                assert(parseResult.isCandidatesConsumed())
                subscriber.onCandidatesConsumed(parseResult.asCandidatesConsumed())
                State.CHECK
            }

            State.CHECK -> {
                assert(parseResult.isCheck())
                parseResult.asCheck().let {
                    subscriber.onCheck(it)
                    if (it.value.value) {
                        defaultState
                    } else {
                        State.SOLVE
                    }
                }
            }

            State.SOLVE -> {
                assert(parseResult.isSolve())
                parseResult.asSolve().let {
                    subscriber.onSolve(it)
                    when (it.value.value) {
                        KSolverStatus.UNSAT -> State.UPDATE
                        else -> defaultState
                    }
                }
            }

            State.UPDATE -> {
                assert(parseResult.isUpdate())
                subscriber.onUpdate(parseResult.asUpdate())
                defaultState
            }
        }

        currentBenchStates[bench] = newState
    }

    suspend fun run(path: Path) {
        var first = true
        path.forEachLine {
            if (first) {
                withCandidatesState = it.contains("index candidates consumed")
                first = false
            }
            consume(it)
        }
    }
}
