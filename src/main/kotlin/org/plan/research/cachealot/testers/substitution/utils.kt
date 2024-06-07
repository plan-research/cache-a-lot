package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import org.plan.research.cachealot.structEquals
import org.plan.research.cachealot.testers.substitution.impl.SubstitutionMonadImpl

fun PersistentMap<KDecl<*>, KDecl<*>>.join(other: PersistentMap<KDecl<*>, KDecl<*>>): PersistentMap<KDecl<*>, KDecl<*>>? {
    val (smaller, greater) = if (size > other.size) {
        other to this
    } else {
        this to other
    }
    smaller.forEach { (key, value) ->
        greater[key]?.let {
            if (!(it structEquals value)) return null
        }
    }
    return putAll(other)
}

fun <K, V> PersistentMap<K, V>.removeAll(keys: Collection<K>): PersistentMap<K, V> =
    mutate { keys.forEach { remove(it) } }

fun <K, V> PersistentMap<K, V>.extractAll(keys: Collection<K>): PersistentMap<K, V> = let { old ->
    persistentHashMapOf<K, V>().mutate {
        keys.forEach { key -> old[key]?.let { put(key, it) } }
    }
}

fun <T : SubstitutionMonadState<T>> SubstitutionMonad<T>.wrap() = SubstitutionMonadHolder(this)
fun <T : SubstitutionMonadState<T>> T.wrap() = SubstitutionMonadHolder(SubstitutionMonadImpl(this))
