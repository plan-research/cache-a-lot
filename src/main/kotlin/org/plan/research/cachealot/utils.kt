package org.plan.research.cachealot

import io.ksmt.decl.KDecl
import io.ksmt.decl.KParameterizedFuncDecl
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random.Default.nextInt

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

// It could be optimized to O( n * log(n) ) (now it's O(n^2))
fun <T> List<T>.randomSequence(): Sequence<T> = sequence {
    val peaked = mutableSetOf<Int>()
    forEach {
        var index = nextInt(size - peaked.size)
        val iter = peaked.iterator()
        while (iter.hasNext() && index >= iter.next()) {
            index++
        }
        yield(get(index))
        assert(peaked.add(index))
    }
}

suspend inline fun checkActive() {
    coroutineContext.ensureActive()
}
