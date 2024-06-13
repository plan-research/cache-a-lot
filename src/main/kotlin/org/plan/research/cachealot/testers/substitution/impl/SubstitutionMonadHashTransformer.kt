package org.plan.research.cachealot.testers.substitution.impl

import io.ksmt.decl.KDecl
import io.ksmt.expr.KExpr
import org.plan.research.cachealot.hash.KExprHasher
import org.plan.research.cachealot.testers.substitution.SubstitutionMonad
import org.plan.research.cachealot.testers.substitution.SubstitutionMonadState
import org.plan.research.cachealot.testers.substitution.substitutionAssert

class SubstitutionMonadHashTransformer<T : SubstitutionMonadState<T>>(
    val monad: SubstitutionMonad<T>,
    val lhsHasher: KExprHasher,
    val rhsHasher: KExprHasher,
) : SubstitutionMonad<T>() {
    override var state: T by monad::state

    override fun copy(): SubstitutionMonad<T> = SubstitutionMonadHashTransformer(monad.copy(), lhsHasher, rhsHasher)

    override fun eqDecl(lhs: KDecl<*>, rhs: KDecl<*>) = monad.eqDecl(lhs, rhs)

    override fun eqExpr(lhs: KExpr<*>, rhs: KExpr<*>) {
        substitutionAssert { lhsHasher.computeHash(lhs) == rhsHasher.computeHash(rhs) }
        monad.eqExpr(lhs, rhs)
    }
}
