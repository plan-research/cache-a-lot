package org.plan.research.cachealot

import io.ksmt.decl.KDecl
import io.ksmt.decl.KParameterizedFuncDecl

infix fun KDecl<*>?.structEquals(other: KDecl<*>?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false
    if (javaClass != other.javaClass) return false

    if (name != other.name) return false
    if (sort != other.sort) return false
    if (argSorts != other.argSorts) return false
    if (this is KParameterizedFuncDecl && parameters != (other as? KParameterizedFuncDecl)?.parameters) return false

    return true
}

class CachedSequence<T>(private val iterator: Iterator<T>) : Sequence<T> {
    private val container: MutableList<T> = mutableListOf()

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var nextIndex = 0

            override fun hasNext(): Boolean {
                if (nextIndex < container.size) return true
                return iterator.hasNext()
            }

            override fun next(): T {
                if (nextIndex < container.size) return container[nextIndex++]
                val next = iterator.next()
                container.add(next)
                nextIndex++
                return next
            }

        }
    }
}

fun <T> Iterator<T>.toCachedSequence(): CachedSequence<T> = CachedSequence(this)
