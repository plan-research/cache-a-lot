package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import org.plan.research.cachealot.structEquals

fun PersistentMap<KDecl<*>, KDecl<*>>.merge(other: PersistentMap<KDecl<*>, KDecl<*>>): PersistentMap<KDecl<*>, KDecl<*>> {
    val (smaller, greater) = if (size > other.size) {
        other to this
    } else {
        this to other
    }
    smaller.forEach { (key, value) ->
        greater[key]?.let {
            substitutionAssert { it structEquals value }
        }
    }
    return putAll(other)
}

fun <T : SubstitutionMonadState<T>> SubstitutionMonad<T>.wrap() = SubstitutionMonadHolder(this)
fun <T : SubstitutionMonadState<T>> T.wrap() = SubstitutionMonadHolder(this)
