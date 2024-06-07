package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl
import io.ksmt.expr.*
import org.plan.research.cachealot.structEquals

abstract class SubstitutionMonad<T : SubstitutionMonadState<T>> {
    abstract var state: T

    abstract fun copy(): SubstitutionMonad<T>

    inline fun scoped(decls: List<KDecl<*>>, block: () -> Unit) {
        val extracted = state.extract(decls)
        try {
            state = state.removeAll(decls)
            block()
        } finally {
            state = state.removeAll(decls).merge(extracted)
        }
    }

    abstract fun eq(lhs: KDecl<*>, rhs: KDecl<*>)
    infix fun KDecl<*>.eq(other: KDecl<*>) = eq(this, other)

    infix fun List<KDecl<*>>.eqDecls(other: List<KDecl<*>>) {
        substitutionAssert { size == other.size }
        for (i in indices) {
            this[i] eq other[i]
        }
    }

    abstract fun eq(lhs: KExpr<*>, rhs: KExpr<*>)
    infix fun KExpr<*>.eq(other: KExpr<*>) = eq(this, other)

    infix fun List<KExpr<*>>.eqExprs(other: List<KExpr<*>>) {
        substitutionAssert { size == other.size }
        for (i in indices) {
            this[i] eq other[i]
        }
    }

}

