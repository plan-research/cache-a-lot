package org.plan.research.cachealot.testers

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import io.ksmt.expr.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.structEquals

class KFullTester(private val ctx: KContext) : KUnsatTester {
    class SubstitutionException : RuntimeException()

    private data class SubstitutionMonadHolder(private val monad: SubstitutionMonad) {
        inline fun run(block: SubstitutionMonad.() -> Unit): SubstitutionMonadHolder? {
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
    private data class SubstitutionMonad(private var declMap: PersistentMap<KDecl<*>, KDecl<*>>) {

        inline fun scoped(decls: List<KDecl<*>> = emptyList(), block: () -> Unit) {
            val old = declMap
            try {
                declMap = decls.fold(declMap) { acc, decl -> acc.remove(decl) }
                block()
            } finally {
                declMap = old
            }
        }

        private fun fail() {
            throw SubstitutionException()
        }

        infix fun KDecl<*>.eq(decl: KDecl<*>) {
            assert { sort == decl.sort }
            if (!(declMap[this] structEquals decl)) {
                assert { this !in declMap }
                declMap = declMap + (this to decl)
            }
        }

        infix fun List<KDecl<*>>.eqDecls(decls: List<KDecl<*>>) {
            assert { size == decls.size }
            for (i in indices) {
                get(i) eq decls[i]
            }
        }

        infix fun List<KExpr<*>>.eqExprs(exprs: List<KExpr<*>>) {
            assert { size == exprs.size }
            for (i in indices) {
                get(i) eq exprs[i]
            }
        }

        infix fun KExpr<*>.eq(expr: KExpr<*>) {
            assert { this::class == expr::class }
            assert { sort == expr.sort }
            when (this) {
                is KFunctionApp<*> -> {
                    expr as KFunctionApp<*>
                    if (args.isEmpty() && expr.args.isEmpty()) {
                        // variable
                        decl eq expr.decl
                    } else {
                        assert { decl structEquals expr.decl }
                    }
                    args eqExprs expr.args
                }

                is KApp<*, *> -> {
                    expr as KApp<*, *>
                    assert { decl structEquals expr.decl }
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

                else -> fail()
            }
        }

        inline fun assert(block: () -> Boolean) {
            if (!block()) fail()
        }
    }

    private data class StackEntry(val index: Int, val monad: SubstitutionMonadHolder)

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val queue = ArrayDeque<StackEntry>()
        queue.addLast(StackEntry(0, SubstitutionMonadHolder(SubstitutionMonad(persistentMapOf()))))
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