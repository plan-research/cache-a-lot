package org.plan.research.cachealot.scripts.cache

import io.ksmt.solver.KSolverStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

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
        println("Conflict: $name")
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

fun run(path1: Path, path2: Path) {
    println("-------------------------------")
    println("${path1.name} - ${path2.name}:")
    runBlocking {
        launch {
            LinearStatsParser(
                VerificationSubscriber()
            ).run(path1 / "stats.log")
        }

        LinearStatsParser(
            VerificationSubscriber()
        ).run(path2 / "stats.log")
    }
    println("Done!")
    println("-------------------------------")
    println()
}

fun main(args: Array<String>) {
    when (args.size) {
        3 -> {
            val commonPath = Path(args[0])
            val dataPath1 = commonPath / args[1]
            val dataPath2 = commonPath / args[2]
            run(dataPath1, dataPath2)
        }

        2 -> {
            val path = Path(args[0])
            val dataPath2 = Path(args[1])
            if (path.listDirectoryEntries().find { it.name == "stats.log" } == null) {
                path.forEachDirectoryEntry {
                    if (it.name != dataPath2.name) {
                        run(it, path / dataPath2)
                    }
                }
            } else {
                run(path, dataPath2)
            }
        }

        1 -> {
            val path = Path(args[0])
            val paths = path.listDirectoryEntries()
            for (i in 0 until paths.size) {
                for (j in i + 1 until paths.size) {
                    run(paths[i], paths[j])
                }
            }
        }
    }
}