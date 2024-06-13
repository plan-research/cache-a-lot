package org.plan.research.cachealot.hash

import io.ksmt.expr.KExpr

interface KExprHasher {
    fun computeHash(expr: KExpr<*>): Long
    fun clear()
}
