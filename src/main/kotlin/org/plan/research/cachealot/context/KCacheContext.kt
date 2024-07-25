package org.plan.research.cachealot.context

typealias KEmptyLocal = KLocalCacheContext
typealias KEmptyGlobal = KGlobalCacheContext<*>

interface KGlobalCacheContext<T : KLocalCacheContext> {
    fun createLocal(): T
}

interface KLocalCacheContext {
    fun clear()
}