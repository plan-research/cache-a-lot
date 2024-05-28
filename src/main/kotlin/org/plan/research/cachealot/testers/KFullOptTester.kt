package org.plan.research.cachealot.testers

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import io.ksmt.expr.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.structEquals

class KFullOptTester(private val ctx: KContext) : KUnsatTester {
    class SubstitutionException : RuntimeException()

    private data class SubstitutionMonadHolder(private val monad: SubstitutionMonad = SubstitutionMonad()) {
        inline fun run(block: SubstitutionMonad.() -> Unit): SubstitutionMonadHolder? {
            try {
                val newMonad = monad.copy()
                newMonad.block()
                return SubstitutionMonadHolder(newMonad)
            } catch (e: SubstitutionException) {
                return null
            }
        }

        fun merge(other: SubstitutionMonadHolder): SubstitutionMonadHolder? =
            run { merge(other.monad) }
    }

    // Why? Because I feel that way.
    private data class SubstitutionMonad(private var declMap: PersistentMap<KDecl<*>, KDecl<*>> = persistentMapOf()) {

        infix fun merge(other: SubstitutionMonad) {
            val (smaller, greater) = if (declMap.size > other.declMap.size) {
                other.declMap to declMap
            } else {
                declMap to other.declMap
            }
            smaller.forEach { (key, value) ->
                greater[key]?.let {
                    assert { it structEquals value }
                }
            }
            declMap = greater.putAll(smaller)
        }

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

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val substitutions = unsatCore.fold(listOf(SubstitutionMonadHolder())) { monads, core ->
            if (monads.isEmpty()) return false
            val newMonads = exprs.mapNotNull { SubstitutionMonadHolder().run { core eq it } }
            monads.flatMap { old -> newMonads.mapNotNull { old.merge(it) } }
        }
        return substitutions.isNotEmpty()
    }
}