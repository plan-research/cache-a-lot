package org.plan.research.cachealot.hash

import io.ksmt.expr.KExpr
import java.util.*

interface KExprHasher {
    fun computeHash(expr: KExpr<*>): Long
}