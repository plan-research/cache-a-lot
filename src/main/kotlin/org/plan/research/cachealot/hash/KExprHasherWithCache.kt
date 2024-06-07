package org.plan.research.cachealot.hash

import io.ksmt.expr.KExpr
import java.util.IdentityHashMap

class KExprHasherWithCache(val hasher: KExprHasher) : KExprHasher {
    private val cache = IdentityHashMap<KExpr<*>, Long>()

    override fun computeHash(expr: KExpr<*>): Long =
        cache.getOrPut(expr) { hasher.computeHash(expr) }
}