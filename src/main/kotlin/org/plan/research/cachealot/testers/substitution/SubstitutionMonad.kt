package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl
import io.ksmt.expr.*
import org.plan.research.cachealot.structEquals

data class SubstitutionMonadHolder<T : SubstitutionMonadState<T>>(val monad: SubstitutionMonad<T>) {
    constructor(state: T) : this(SubstitutionMonad(state))

    inline fun run(block: SubstitutionMonad<T>.() -> Unit): SubstitutionMonadHolder<T>? {
        try {
            val newMonad = monad.copy()
            newMonad.block()
            return SubstitutionMonadHolder(newMonad)
        } catch (e: SubstitutionException) {
            return null
        }
    }
}

// Why? Because I feel that way.
data class SubstitutionMonad<T : SubstitutionMonadState<T>>(var state: T) {

    inline fun scoped(decls: List<KDecl<*>> = emptyList(), block: () -> Unit) {
        val extracted = state.extract(decls)
        try {
            state = state.removeAll(decls)
            block()
        } finally {
            state = state.removeAll(decls).merge(extracted)
        }
    }

    infix fun KDecl<*>.eq(decl: KDecl<*>) {
        substitutionAssert { sort == decl.sort }
        if (!state.checkSubstitution(this@eq, decl)) {
            substitutionAssert { !state.hasSubstitutionFor(this) }
            state = state.substitute(this, decl)
        }
    }

    infix fun List<KDecl<*>>.eqDecls(decls: List<KDecl<*>>) {
        substitutionAssert { size == decls.size }
        for (i in indices) {
            get(i) eq decls[i]
        }
    }

    infix fun List<KExpr<*>>.eqExprs(exprs: List<KExpr<*>>) {
        substitutionAssert { size == exprs.size }
        for (i in indices) {
            get(i) eq exprs[i]
        }
    }

    infix fun KExpr<*>.eq(expr: KExpr<*>) {
        substitutionAssert { this::class == expr::class }
        substitutionAssert { sort == expr.sort }
        when (this) {
            is KFunctionApp<*> -> {
                expr as KFunctionApp<*>
                if (args.isEmpty() && expr.args.isEmpty()) {
                    // variable
                    decl eq expr.decl
                } else {
                    substitutionAssert { decl structEquals expr.decl }
                }
                args eqExprs expr.args
            }

            is KApp<*, *> -> {
                expr as KApp<*, *>
                substitutionAssert { decl structEquals expr.decl }
                args eqExprs expr.args
            }

            is KQuantifier -> {
                expr as KQuantifier
                scoped(bounds) {
                    bounds eqDecls expr.bounds
                    body eq expr.body
                }
            }

            is KArrayLambdaBase<*, *> -> {
                expr as KArrayLambdaBase<*, *>
                scoped(indexVarDeclarations) {
                    indexVarDeclarations eqDecls expr.indexVarDeclarations
                    body eq expr.body
                }
            }

            else -> substitutionFail()
        }
    }
}