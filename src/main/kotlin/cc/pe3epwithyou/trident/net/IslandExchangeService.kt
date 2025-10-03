package cc.pe3epwithyou.trident.net

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.config.Config
import cc.pe3epwithyou.trident.state.MCCIState
import cc.pe3epwithyou.trident.state.PlayerStateIO
import cc.pe3epwithyou.trident.utils.ChatUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object IslandExchangeService : ClientTickEvents.EndTick {
    private val executor = Executors.newSingleThreadExecutor()
    private var lastAutoRefreshMs: Long = 0
    private var autoRefreshRegistered: Boolean = false
    init {
        // Always register ticking; logic only runs if autoRefresh is enabled
        ClientTickEvents.END_CLIENT_TICK.register(this)
        autoRefreshRegistered = true
    }

    fun refreshNow(reason: String = "manual") {
        val now = System.currentTimeMillis()
        executor.submit {
            runCatching {
                val active = McciApi.fetchActiveIslandExchangeListings()
                val sold = McciApi.fetchSoldIslandExchangeListings()

                // Versioning: always trust the newest fetch. Replace sets if newer than stored.
                val ps = TridentClient.playerState
                val activeVersion = now
                val soldVersion = now

                if (activeVersion >= ps.exchange.activeVersionMs) {
                    ps.exchange.activeVersionMs = activeVersion
                    ps.exchange.activeListings = active.toMutableList()
                }
                if (soldVersion >= ps.exchange.soldVersionMs) {
                    ps.exchange.soldVersionMs = soldVersion
                    ps.exchange.soldListings = sold.toMutableList()
                }
                PlayerStateIO.save()
                ChatUtils.debugLog("IslandExchangeService: refresh completed (${reason}); active=${active.size}, sold=${sold.size}")
            }.onFailure {
                ChatUtils.warn("IslandExchangeService: refresh failed: ${it.message}")
            }
        }
    }

    fun registerAutoRefreshIfEnabled() { /* kept for compatibility; no-op */ }

    override fun onEndTick(client: Minecraft) {
        if (!Config.Exchange.autoRefresh) return
        if (!MCCIState.isOnIsland()) return
        val minutes = Config.Exchange.autoRefreshMinutes.coerceAtLeast(1)
        val intervalMs = TimeUnit.MINUTES.toMillis(minutes.toLong())
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshMs >= intervalMs) {
            lastAutoRefreshMs = now
            refreshNow(reason = "auto")
        }
    }

    data class PriceStats(val lowestPerUnit: Double?, val avgSoldPerUnit24h: Double?)

    /**
     * Compute lowest listed price per unit and average sold price per unit (24h) for a given item name.
     * Token suffixes are stripped for comparison. Optionally ignore amount > 1 listings.
     */
    fun computePriceStatsForName(nameRaw: String): PriceStats {
        val name = nameRaw.removeSuffix(" Token").trim()
        val ps = TridentClient.playerState
        val ignoreAmount = Config.Exchange.ignoreAmountAboveOne
        val now = System.currentTimeMillis()
        val dayAgo = now - TimeUnit.HOURS.toMillis(24)

        val active = ps.exchange.activeListings.asSequence()
            .filter { it.asset.name.equals(name, true) || it.asset.name.equals("${name} Token", true) }
            .filter { !ignoreAmount || it.amount <= 1 }
            .map { it.cost.toDouble() / it.amount.coerceAtLeast(1) }
            .toList()

        val lowest = active.minOrNull()

        val sold = ps.exchange.soldListings.asSequence()
            .filter { it.asset.name.equals(name, true) || it.asset.name.equals("${name} Token", true) }
            .filter { !ignoreAmount || it.amount <= 1 }
            .map { it.cost.toDouble() / it.amount.coerceAtLeast(1) }
            .toList()

        val avgSold = if (sold.isNotEmpty()) sold.average() else null
        return PriceStats(lowestPerUnit = lowest, avgSoldPerUnit24h = avgSold)
    }
}

private fun McciApi.IslandExchangeListing.creationTimeOrEndMs(): Long {
    fun parseIso(s: String): Long = runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
    // Use endTime if provided; fall back to creationTime
    val end = endTime
    if (!end.isNullOrEmpty()) return parseIso(end)
    return parseIso(creationTime)
}


