package org.plan.research.cachealot.testers

import io.ksmt.KContext
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.testers.substitution.SubstitutionMonadHolder
import org.plan.research.cachealot.testers.substitution.impl.MapSubstitutionMonadState
import org.plan.research.cachealot.testers.substitution.wrap

class KFullTester(private val ctx: KContext) : KUnsatTester {

    private fun createMonad() = MapSubstitutionMonadState().wrap().wrap()

    private data class StackEntry(val index: Int, val monad: SubstitutionMonadHolder<MapSubstitutionMonadState>)

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val queue = ArrayDeque<StackEntry>()
        queue.addLast(StackEntry(0, createMonad()))
        while (queue.isNotEmpty()) {
            val (index, monad) = queue.removeLast()
            val lhs = unsatCore[index]
            for (rhs in exprs) {
                monad.run {
                    lhs eq rhs
                }?.let {
                    if (index + 1 < unsatCore.size) {
                        queue.addLast(StackEntry(index + 1, it))
                    } else {
                        return true
                    }
                }
            }
        }
        return false
    }
}