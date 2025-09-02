package cc.pe3epwithyou.trident.interfaces.meter

import cc.pe3epwithyou.trident.client.TridentClient

object MeterCalculator {
    // Values read dynamically from Config each update
    fun applyXpToDaily(xp: Int) {
        val st = TridentClient.playerState.dailyMeter
        rollProgress(st, xp) { claimIndex -> dailyTargetFor(claimIndex) }
        updateRates(st, xp)
    }

    fun applyXpToWeekly(xp: Int) {
        val st = TridentClient.playerState.weeklyMeter
        rollProgress(st, xp) { claimIndex -> weeklyTargetFor(claimIndex) }
        updateRates(st, xp)
    }

    private fun rollProgress(
        st: cc.pe3epwithyou.trident.state.MeterState,
        xp: Int,
        targetFor: (Int) -> Int,
    ) {
        // If already at max claims, mirror GUI: keep claims at max and bar full
        if (st.claimsMax != 0 && st.claimsCurrent >= st.claimsMax) {
            if (st.progressTarget > 0) st.progressCurrent = st.progressTarget
            return
        }
        var remain = xp
        var current = st.progressCurrent
        var claims = st.claimsCurrent
        val maxClaims = st.claimsMax
        var target = targetFor(claims + 1)
        st.progressTarget = target
        while (remain > 0) {
            val step = if (target > 0) target - current else remain
            val apply = kotlin.math.min(remain, step)
            current += apply
            remain -= apply
            if (target > 0 && current >= target) {
                if (maxClaims == 0 || claims < maxClaims) {
                    claims += 1
                }
                if (maxClaims != 0 && claims >= maxClaims) {
                    // Hit max claims: set progress to max target and stop
                    current = target
                    st.progressTarget = target
                    break
                } else {
                    current = 0
                    target = targetFor(claims + 1)
                    st.progressTarget = target
                }
            }
        }
        st.progressCurrent = current
        st.claimsCurrent = claims
    }

    private fun updateRates(st: cc.pe3epwithyou.trident.state.MeterState, xp: Int) {
        val shortWindowMs = cc.pe3epwithyou.trident.config.Config.MetersConfig.shortWindowMinutes.toLong() * 60_000L
        val longWindowMs = cc.pe3epwithyou.trident.config.Config.MetersConfig.longWindowMinutes.toLong() * 60_000L
        val shortHalfLifeMs = cc.pe3epwithyou.trident.config.Config.MetersConfig.shortHalfLifeMinutes.toLong() * 60_000L
        val longHalfLifeMs = cc.pe3epwithyou.trident.config.Config.MetersConfig.longHalfLifeMinutes.toLong() * 60_000L
        val now = System.currentTimeMillis()
        st.xpEvents.add(Pair(now, xp))
        // drop events outside the longest window
        val cutoff = now - kotlin.math.max(shortWindowMs, longWindowMs)
        while (st.xpEvents.isNotEmpty() && st.xpEvents.first().first < cutoff) {
            st.xpEvents.removeAt(0)
        }
        fun rollingRate(windowMs: Long): Double {
            val cutoffW = now - windowMs
            var sum = 0
            st.xpEvents.forEach { (t, v) -> if (t >= cutoffW) sum += v }
            val hours = windowMs / 1000.0 / 3600.0
            return if (hours > 0) sum / hours else 0.0
        }
        val shortRate = rollingRate(shortWindowMs)
        val longRate = rollingRate(longWindowMs)

        // EMA update based on elapsed time
        val dtMs = (now - st.lastEmaUpdateMs).coerceAtLeast(1)
        fun emaUpdate(prev: Double, target: Double, halfLifeMs: Long): Double {
            val alpha = 1.0 - kotlin.math.exp(-dtMs.toDouble() / halfLifeMs.toDouble())
            return (1 - alpha) * prev + alpha * target
        }
        st.emaShortXpPerHour = emaUpdate(st.emaShortXpPerHour, shortRate, shortHalfLifeMs)
        st.emaLongXpPerHour = emaUpdate(st.emaLongXpPerHour, longRate, longHalfLifeMs)
        st.lastEmaUpdateMs = now
    }

    private fun dailyTargetFor(claimIndex: Int): Int {
        return when (claimIndex) {
            1, 2 -> 500
            3, 4 -> 1000
            5, 6 -> 2000
            in 7..10 -> 3000
            in 11..14 -> 4000
            15 -> 5000
            else -> 5000
        }
    }

    private fun weeklyTargetFor(claimIndex: Int): Int {
        return when (claimIndex) {
            in 1..5 -> 1000
            in 6..10 -> 1500
            in 11..15 -> 2000
            in 16..20 -> 3000
            in 21..25 -> 4000
            in 26..30 -> 5000
            in 31..35 -> 6000
            in 36..40 -> 7000
            in 41..45 -> 8000
            in 46..50 -> 9000
            in 51..55 -> 10000
            in 56..60 -> 12000
            else -> 12000
        }
    }
}


