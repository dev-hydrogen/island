package cc.pe3epwithyou.trident.client.listeners

import cc.pe3epwithyou.trident.interfaces.DialogCollection
import cc.pe3epwithyou.trident.state.MCCIState
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object UtilitiesRefreshListener : ClientTickEvents.EndTick {
    private var lastRefreshAtMs: Long = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(this)
    }

    override fun onEndTick(client: Minecraft) {
        if (!MCCIState.isOnIsland()) return

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastRefreshAtMs < 1000L) return
        lastRefreshAtMs = nowMs

        if (DialogCollection.get("utilities") != null) {
            DialogCollection.refreshDialog("utilities")
        }
    }
}


