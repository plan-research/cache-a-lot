package org.plan.research.cachealot.scripts

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

enum class ExecutionMode {
    NONE_PARALLEL, SMT_PARALLEL, BENCH_PARALLEL, ALL_PARALLEL
}

class BenchmarkExecutor<Context>(private val contextBuilder: (String) -> Context) {
    private var onNewBenchmark: suspend Context.(String) -> Boolean = { true }
    private var onBenchmarkEnd: suspend Context.(String) -> Unit = { }
    private var onNewSmtFile: suspend Context.(Path) -> Unit = { }
    private var onEnd: suspend () -> Unit = { }

    fun onNewBenchmark(action: suspend Context.(String) -> Boolean): BenchmarkExecutor<Context> {
        this.onNewBenchmark = action
        return this
    }

    fun onBenchmarkEnd(action: suspend Context.(String) -> Unit): BenchmarkExecutor<Context> {
        this.onBenchmarkEnd = action
        return this
    }

    fun onNewSmtFile(action: suspend Context.(Path) -> Unit): BenchmarkExecutor<Context> {
        this.onNewSmtFile = action
        return this
    }

    fun onEnd(action: suspend () -> Unit): BenchmarkExecutor<Context> {
        this.onEnd = action
        return this
    }

    fun execute(mode: ExecutionMode, coroutineContext: CoroutineContext, path: Path) {
        when (mode) {
            ExecutionMode.NONE_PARALLEL -> executeNoneParallel(coroutineContext, path)
            ExecutionMode.SMT_PARALLEL -> executeSmtParallel(coroutineContext, path)
            ExecutionMode.BENCH_PARALLEL -> executeBenchParallel(coroutineContext, path)
            ExecutionMode.ALL_PARALLEL -> executeAllParallel(coroutineContext, path)
        }
    }

    private inline fun Path.forEachBench(block: (Path) -> Unit) {
        listDirectoryEntries().sorted().forEach(block)
    }

    private inline fun Path.forEachSmtFile(block: (Path) -> Unit) {
        listDirectoryEntries().sortedBy { it.nameWithoutExtension.substringAfter('-').toInt() }.forEach(block)
    }

    private fun executeNoneParallel(coroutineContext: CoroutineContext, path: Path) {
        runBlocking(coroutineContext) {
            path.forEachBench { folder ->
                val benchName = folder.name
                with(contextBuilder(benchName)) {
                    if (onNewBenchmark(benchName)) {
                        folder.forEachSmtFile { path ->
                            onNewSmtFile(path)
                        }
                        onBenchmarkEnd(benchName)
                    }
                }
            }
            onEnd()
        }
    }

    private fun executeSmtParallel(coroutineContext: CoroutineContext, path: Path) {
        runBlocking(coroutineContext) {
            path.forEachBench { folder ->
                coroutineScope {
                    val benchName = folder.name
                    with(contextBuilder(benchName)) {
                        if (onNewBenchmark(benchName)) {
                            coroutineScope {
                                folder.forEachSmtFile { path ->
                                    launch { onNewSmtFile(path) }
                                }
                            }
                            onBenchmarkEnd(benchName)
                        }
                    }
                }
            }
            onEnd()
        }
    }

    private fun executeBenchParallel(coroutineContext: CoroutineContext, path: Path) {
        runBlocking(coroutineContext) {
            coroutineScope {
                path.forEachBench { folder ->
                    launch {
                        val benchName = folder.name
                        with(contextBuilder(benchName)) {
                            if (onNewBenchmark(benchName)) {
                                folder.forEachSmtFile { path ->
                                    onNewSmtFile(path)
                                }
                                onBenchmarkEnd(benchName)
                            }
                        }
                    }
                }
            }
            onEnd()
        }
    }

    private fun executeAllParallel(coroutineContext: CoroutineContext, path: Path) {
        runBlocking(coroutineContext) {
            coroutineScope {
                path.forEachBench { folder ->
                    launch {
                        val benchName = folder.name
                        with(contextBuilder(benchName)) {
                            if (onNewBenchmark(benchName)) {
                                coroutineScope {
                                    folder.forEachSmtFile { path ->
                                        launch { onNewSmtFile(path) }
                                    }
                                }
                                onBenchmarkEnd(benchName)
                            }
                        }
                    }
                }
            }
            onEnd()
        }
    }

}