package org.plan.research.cachealot.testers.substitution

import org.plan.research.cachealot.testers.substitution.impl.SubstitutionMonadImpl

data class SubstitutionMonadHolder<T : SubstitutionMonadState<T>>(val monad: SubstitutionMonad<T>) {
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
