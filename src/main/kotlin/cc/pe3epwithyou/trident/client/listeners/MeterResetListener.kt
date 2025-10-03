package cc.pe3epwithyou.trident.client.listeners

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.state.MCCIState
import cc.pe3epwithyou.trident.interfaces.DialogCollection
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object MeterResetListener : ClientTickEvents.EndTick {
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(this)
    }

    override fun onEndTick(client: Minecraft) {
        if (!MCCIState.isOnIsland()) return
        val now = System.currentTimeMillis()
        fun maybeReset(st: cc.pe3epwithyou.trident.state.MeterState): Boolean {
            val next = st.nextResetAtMs
            if (next <= 0L) return false
            if (st.lastAppliedResetAtMs >= next) return false
            if (now < next) return false
            st.progressCurrent = 0
            st.progressTarget = 0
            st.claimsCurrent = 0
            st.lastAppliedResetAtMs = next
            return true
        }
        var changed = false
        changed = maybeReset(TridentClient.playerState.dailyMeter) || changed
        changed = maybeReset(TridentClient.playerState.weeklyMeter) || changed
        if (changed) DialogCollection.refreshDialog("meter")
    }
}


