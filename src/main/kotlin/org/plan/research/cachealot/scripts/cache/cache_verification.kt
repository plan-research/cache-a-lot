package org.plan.research.cachealot.scripts.cache

import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.time.TimedValue

class VerificationSubscriber : LinearStatsParserSubscriber {
    override suspend fun onCheck(parseResult: ParseResultCheck) {
        if (parseResult.value.value) {
            check(parseResult.name, KSolverStatus.UNSAT)
        }
    }

    override suspend fun onSolve(parseResult: ParseResultSolve) {
        check(parseResult.name, parseResult.value.value)
    }

    override suspend fun onUpdate(parseResult: ParseResultUpdate) {}

    override suspend fun onCandidatesConsumed(parseResult: ParseResultCandidatesConsumed) {}

    private fun conflict(name: String) {
        System.err.println("Conflict: $name")
    }

    private suspend fun check(name: String, status: KSolverStatus) {
        results.compute(name) { _, currentStatus ->
            when (currentStatus) {
                null, KSolverStatus.UNKNOWN -> status
                KSolverStatus.SAT -> {
                    if (status == KSolverStatus.UNSAT) conflict(name)
                    currentStatus
                }

                KSolverStatus.UNSAT -> {
                    if (status == KSolverStatus.SAT) conflict(name)
                    currentStatus
                }
            }
        }
    }

    companion object {
        private val results = ConcurrentHashMap<String, KSolverStatus>()
    }
}

fun main(args: Array<String>) {
    var dataPath1: Path
    var dataPath2: Path

    if (args.size == 3) {
        val commonPath = Path(args[0])
        dataPath1 = commonPath / args[1]
        dataPath2 = commonPath / args[2]
    } else {
        dataPath1 = Path(args[0])
        dataPath2 = Path(args[1])
    }
    dataPath1 = dataPath1 / "stats.log"
    dataPath2 = dataPath2 / "stats.log"

    runBlocking {
        launch {
            LinearStatsParser(
                VerificationSubscriber()
            ).run(dataPath1)
        }

        LinearStatsParser(
            VerificationSubscriber()
        ).run(dataPath2)
    }

}