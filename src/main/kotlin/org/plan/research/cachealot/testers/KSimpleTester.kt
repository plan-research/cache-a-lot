package org.plan.research.cachealot.testers

import io.ksmt.expr.KApp
import io.ksmt.expr.KArrayLambdaBase
import io.ksmt.expr.KExpr
import io.ksmt.expr.KQuantifier
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KEmptyLocal
import org.plan.research.cachealot.structEquals

class KSimpleTester : KUnsatTester<KEmptyLocal> {

    private inline fun <T> listEquals(
        lhs: List<T>, rhs: List<T>,
        eq: (T, T) -> Boolean = { l, r -> l == r }
    ): Boolean = lhs.size == rhs.size && lhs.zip(rhs).all { eq(it.first, it.second) }

    private suspend fun equals(lhs: KExpr<*>, rhs: KExpr<*>): Boolean {
        if (lhs::class != rhs::class || lhs.sort != rhs.sort) return false
        return when (lhs) {
            is KApp<*, *> -> {
                rhs as KApp<*, *>
                lhs.decl structEquals rhs.decl &&
                        listEquals(lhs.args, rhs.args) { l, r -> equals(l, r) }
            }

            is KQuantifier -> {
                rhs as KQuantifier
                listEquals(lhs.bounds, rhs.bounds) { l, r -> l structEquals r } &&
                        equals(lhs.body, rhs.body)
            }

            is KArrayLambdaBase<*, *> -> {
                rhs as KArrayLambdaBase<*, *>
                listEquals(lhs.indexVarDeclarations, rhs.indexVarDeclarations) { l, r -> l structEquals r } &&
                        equals(lhs.body, rhs.body)
            }

            else -> false
        }
    }

    override suspend fun test(ctx: KEmptyLocal, unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        return unsatCore.all { expr -> exprs.any { equals(expr, it) } }
    }
}
