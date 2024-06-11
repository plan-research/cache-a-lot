package org.plan.research.cachealot.testers.substitution.impl

import io.ksmt.decl.KDecl
import io.ksmt.expr.*
import org.plan.research.cachealot.structEquals
import org.plan.research.cachealot.testers.substitution.SubstitutionMonad
import org.plan.research.cachealot.testers.substitution.SubstitutionMonadState
import org.plan.research.cachealot.testers.substitution.substitutionAssert
import org.plan.research.cachealot.testers.substitution.substitutionFail

open class SubstitutionMonadImpl<T : SubstitutionMonadState<T>>(override var state: T) :
    SubstitutionMonad<T>() {

    override fun copy(): SubstitutionMonad<T> =
        SubstitutionMonadImpl(state)

    override fun eqDecl(lhs: KDecl<*>, rhs: KDecl<*>) {
        substitutionAssert { lhs.sort == rhs.sort }
        if (!state.checkSubstitution(lhs, rhs)) {
            substitutionAssert { !state.hasSubstitutionFor(lhs) }
            state = state.substitute(lhs, rhs)
        }
    }

    override fun eqExpr(lhs: KExpr<*>, rhs: KExpr<*>) {
        substitutionAssert { lhs::class == rhs::class }
        substitutionAssert { lhs.sort == rhs.sort }
        when (lhs) {
            is KFunctionApp<*> -> {
                rhs as KFunctionApp<*>
                if (lhs.args.isEmpty() && rhs.args.isEmpty()) {
                    // variable
                    lhs.decl eq rhs.decl
                } else {
                    substitutionAssert { lhs.decl structEquals rhs.decl }
                }
                lhs.args eqExprs rhs.args
            }

            is KApp<*, *> -> {
                rhs as KApp<*, *>
                substitutionAssert { lhs.decl structEquals rhs.decl }
                lhs.args eqExprs rhs.args
            }

            is KQuantifier -> {
                rhs as KQuantifier
                scoped(lhs.bounds) {
                    lhs.bounds eqDecls rhs.bounds
                    lhs.body eq rhs.body
                }
            }

            is KArrayLambdaBase<*, *> -> {
                rhs as KArrayLambdaBase<*, *>
                scoped(lhs.indexVarDeclarations) {
                    lhs.indexVarDeclarations eqDecls rhs.indexVarDeclarations
                    lhs.body eq rhs.body
                }
            }

            else -> substitutionFail()
        }
    }
}
