package org.plan.research.cachealot.testers

import io.ksmt.expr.KApp
import io.ksmt.expr.KArrayLambdaBase
import io.ksmt.expr.KExpr
import io.ksmt.expr.KQuantifier
import org.plan.research.cachealot.KBoolExprs

class KSimpleTester : KUnsatTester {

    private inline fun <T> listEquals(
        lhs: List<T>, rhs: List<T>,
        eq: (T, T) -> Boolean = { l, r -> l == r }
    ): Boolean = lhs.size == rhs.size && lhs.zip(rhs).all { eq(it.first, it.second) }

    private suspend fun equals(lhs: KExpr<*>, rhs: KExpr<*>): Boolean {
        if (lhs::class != rhs::class || lhs.sort != rhs.sort) return false
        return when (lhs) {
            is KApp<*, *> -> {
                rhs as KApp<*, *>
                lhs.decl == rhs.decl &&
                        listEquals(lhs.args, rhs.args) { l, r -> equals(l, r) }
            }

            is KQuantifier -> {
                rhs as KQuantifier
                listEquals(lhs.bounds, rhs.bounds) &&
                        equals(lhs.body, rhs.body)
            }

            is KArrayLambdaBase<*, *> -> {
                rhs as KArrayLambdaBase<*, *>
                listEquals(lhs.indexVarDeclarations, rhs.indexVarDeclarations) &&
                        equals(lhs.body, rhs.body)
            }

            else -> false
        }
    }

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        return unsatCore.all { expr -> exprs.any { equals(expr, it) } }
    }
}
