package com.nowtuneup.app.domain.connection

import kotlin.math.min

object ReconnectBackoff {
    fun delaySeconds(baseSeconds: Int, maximumSeconds: Int, attemptIndex: Int): Int {
        require(baseSeconds > 0)
        require(maximumSeconds >= baseSeconds)
        require(attemptIndex >= 0)
        val multiplier = 1 shl attemptIndex.coerceAtMost(20)
        return min(baseSeconds.toLong() * multiplier, maximumSeconds.toLong()).toInt()
    }
}
