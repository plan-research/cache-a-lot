package org.plan.research.cachealot.index.flat

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.plan.research.cachealot.KBoolExprs
import kotlin.math.min
import kotlin.random.Random.Default.nextInt

class KRandomIndex(private val numberOfCandidates: Int) : KFlatIndex() {
    private val mutex = Mutex()
    private var candidates = persistentListOf<KBoolExprs>()

    override suspend fun getCandidates(): Flow<KBoolExprs> = flow {
        val current = mutex.withLock { candidates }
        val num = min(numberOfCandidates, current.size)

        // It could be optimized to O( n * log(n) ) (now it's O(n^2))
        val peaked = mutableSetOf<Int>()
        repeat(num) {
            var index = nextInt(current.size - peaked.size)
            val iter = peaked.iterator()
            while (iter.hasNext() && index >= iter.next()) {
                index++
            }
            emit(current[index])
            assert(peaked.add(index))
        }
    }

    override suspend fun insert(value: KBoolExprs) {
        mutex.withLock { candidates = candidates.add(value) }
    }
}
