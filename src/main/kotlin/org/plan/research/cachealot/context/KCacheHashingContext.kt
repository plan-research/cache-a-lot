package org.plan.research.cachealot.context

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.hash.KExprHasherImpl

class KCacheHashingContext : KGlobalCacheContext<KCacheHashingContext.Local> {

    companion object {
        const val MAX_HEIGHT = 7
    }

    val coreHasher = KExprHasherImpl(MAX_HEIGHT)

    inner class Local : KLocalCacheContext {
        val exprHasher = KExprHasherImpl(MAX_HEIGHT)
        val coreHasher by this@KCacheHashingContext::coreHasher

        private val hash2Exprs = hashMapOf<Long, MutableList<KExpr<KBoolSort>>>()

        fun getOrComputeHash2Exprs(exprs: KBoolExprs): HashMap<Long, MutableList<KExpr<KBoolSort>>> {
            if (hash2Exprs.isNotEmpty()) return hash2Exprs
            exprs.forEach { hash2Exprs.getOrPut(exprHasher.computeHash(it)) { mutableListOf() }.add(it) }
            return hash2Exprs
        }

        override fun clear() {
            exprHasher.clear()
        }
    }

    override fun createLocal(): Local = Local()

}