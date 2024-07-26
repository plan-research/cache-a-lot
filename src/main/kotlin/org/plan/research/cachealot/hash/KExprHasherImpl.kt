package org.plan.research.cachealot.hash

import io.ksmt.expr.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

class KExprHasherImpl(val maxHeight: Int = UNLIMITED) : KExprHasher {

    private class IdentityWrapper(val expr: KExpr<*>) {
        override fun hashCode() = System.identityHashCode(expr)
        override fun equals(other: Any?): Boolean {
            if (other == null || other !is IdentityWrapper) return false
            return expr === other.expr
        }
    }

    private fun KExpr<*>.wrap() = IdentityWrapper(this)

    private val cache = ConcurrentHashMap<IdentityWrapper, Channel<ReturnEntry?>>()
    private suspend fun getFromCache(expr: KExpr<*>): ReturnEntry? {
        val channel = cache.computeIfAbsent(expr.wrap()) {
            Channel<ReturnEntry?>(1).apply {
                trySend(null)
            }
        }
        return channel.receive()
    }

    private suspend fun putIntoCache(expr: KExpr<*>, entry: ReturnEntry) {
        val channel = cache.computeIfAbsent(expr.wrap()) {
            Channel(1)
        }
        channel.tryReceive()
        channel.send(entry)
    }

    companion object {
        private const val HASH_SHIFT = 31L
        const val UNLIMITED = -1
    }

    private fun concatHashes(vararg hashs: Number): Long =
        hashs.fold(0L) { acc, hash -> acc * HASH_SHIFT + hash.toLong() }

    private fun computeContentHash(expr: KExpr<*>): Long = when (expr) {
        is KFunctionApp<*> -> if (expr.args.isEmpty()) {
            concatHashes(expr.sort.hashCode())
        } else {
            concatHashes(expr.sort.hashCode(), expr.decl.name.hashCode(), expr.args.size)
        }

        is KApp<*, *> ->
            concatHashes(expr.sort.hashCode(), expr::class.hashCode())

        is KQuantifier ->
            concatHashes(expr.sort.hashCode(), expr.bounds.size, expr::class.hashCode())

        is KArrayLambdaBase<*, *> ->
            concatHashes(expr.sort.hashCode(), expr.indexVarDeclarations.size, expr::class.hashCode())

        else -> concatHashes(expr.sort.hashCode(), expr::class.hashCode())
    }

    private data class StackEntry(val expr: KExpr<*>, var hash: Long = 0, var full: Boolean = true, var state: Int = 0)
    private data class ReturnEntry(val hash: Long, val full: Boolean)

    override suspend fun computeHash(expr: KExpr<*>): Long {
        getFromCache(expr)?.let {
            putIntoCache(expr, it)
            return it.hash
        }
        val stack = ArrayDeque<StackEntry>()
        stack.add(StackEntry(expr))
        var ret: ReturnEntry? = null
        while (stack.isNotEmpty()) {
            val entry = stack.last()
            if (entry.state == 0) {
                entry.hash = computeContentHash(entry.expr)
            }
            ret?.let {
                entry.hash = concatHashes(entry.hash, it.hash)
                entry.full = entry.full && it.full
                ret = null
            }
            val next = when (val e = entry.expr) {
                is KApp<*, *> -> if (entry.state < e.args.size) {
                    e.args[entry.state]
                } else null

                is KQuantifier -> if (entry.state < 1) {
                    e.body
                } else null

                is KArrayLambdaBase<*, *> -> if (entry.state < 1) {
                    e.body
                } else null

                else -> null
            }
            if (next == null) {
                ReturnEntry(entry.hash, entry.full).let {
                    if (it.full) {
                        putIntoCache(entry.expr, it)
                    }
                    ret = it
                }
                stack.removeLast()
            } else {
                entry.state++
                if (maxHeight == UNLIMITED || stack.size < maxHeight) {
                    stack.addLast(StackEntry(next))
                } else {
                    ret = ReturnEntry(computeContentHash(next), false)
                }
            }
        }
        putIntoCache(expr, ret!!)
        return ret!!.hash
    }

    override fun clear() {
        cache.clear()
    }

}