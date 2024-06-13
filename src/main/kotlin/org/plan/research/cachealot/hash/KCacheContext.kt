package org.plan.research.cachealot.hash

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.plan.research.cachealot.KBoolExprs

class KCacheContext {

    companion object {
        const val MAX_HEIGHT = 7
    }

    val coreHasher = KExprHasherImpl(MAX_HEIGHT)
    val exprHasher = KExprHasherImpl(MAX_HEIGHT)

    private val hash2Exprs = hashMapOf<Long, MutableList<KExpr<KBoolSort>>>()

    fun getOrComputeHash2Exprs(exprs: KBoolExprs): HashMap<Long, MutableList<KExpr<KBoolSort>>> {
        if (hash2Exprs.isNotEmpty()) return hash2Exprs
        exprs.forEach { hash2Exprs.getOrPut(exprHasher.computeHash(it)) { mutableListOf() }.add(it) }
        return hash2Exprs
    }


    fun clearExprsRelated() {
        exprHasher.clear()
        hash2Exprs.clear()
    }

    fun clearUnsatCoresRelated() {
        coreHasher.clear()
    }

}