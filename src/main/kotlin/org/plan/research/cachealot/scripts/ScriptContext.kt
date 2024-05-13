package org.plan.research.cachealot.scripts

import io.ksmt.KContext
import io.ksmt.solver.portfolio.KPortfolioSolverManager
import io.ksmt.solver.z3.KZ3SMTLibParser
import io.ksmt.solver.z3.KZ3Solver
import kotlin.time.Duration.Companion.seconds

class ScriptContext {
    val ctx = KContext()
    val parser = KZ3SMTLibParser(ctx)
    val timeout = 5.seconds
    val seed = 42
    val poolSize = 8
    val portfolioSolverManager: KPortfolioSolverManager =
        KPortfolioSolverManager(
            solvers = listOf(KZ3Solver::class),
            portfolioPoolSize = poolSize,
            hardTimeout = timeout * 2,
            workerProcessIdleTimeout = 10.seconds,
        )

    init {
        KZ3Solver(KContext()).close()
    }
}