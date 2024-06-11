package org.plan.research.cachealot.metrics.hash

import io.ksmt.expr.KExpr

interface KExprHasher {
    fun computeHash(expr: KExpr<*>): Long
    fun clear()
}
