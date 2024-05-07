package org.plan.research.cachealot

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.getValue
import kotlin.time.Duration.Companion.seconds

fun main() {
    val ctx = KContext()

    with(ctx) {
        val x by boolSort
        val y by intSort
        val z by intSort

        val expr: KExpr<KBoolSort> = x and (y ge z)

        KZ3Solver(ctx).use { solver ->
            solver.assert(expr)
            val result = solver.check(1.seconds)
            println(result)
        }
    }
}