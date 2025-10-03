package cc.pe3epwithyou.trident.client.listeners

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.interfaces.DialogCollection
import cc.pe3epwithyou.trident.state.fishing.UpgradeLine
import cc.pe3epwithyou.trident.mixin.BossHealthOverlayAccessor
import cc.pe3epwithyou.trident.utils.ChatUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.BossHealthOverlay
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import java.util.*

class TideWindBossbarListener : ClientTickEvents.EndTick {
    private var lastSeen: String = ""

    companion object {
        fun register() {
            ClientTickEvents.END_CLIENT_TICK.register(TideWindBossbarListener())
        }
    }

    override fun onEndTick(client: Minecraft) {
        val gui = client.gui ?: return
        val overlay: BossHealthOverlay = gui.bossOverlay
        val accessor = overlay as BossHealthOverlayAccessor
        val events: Map<UUID, LerpingBossEvent> = accessor.events
        val titles = events.values.map { it.name.string }.joinToString(" | ")
        if (titles == lastSeen) return
        lastSeen = titles

        val ps = TridentClient.playerState
        ps.inGrotto = titles.contains("Stability:")

        // Time: HH:MM in 10-minute steps, compute ingame minutes and day/night
        val timeRegex = Regex("(?i)(\\d{2}):(\\d{2})")
        timeRegex.find(titles)?.let { m ->
            val hh = m.groupValues[1].toIntOrNull() ?: 0
            val mm = m.groupValues[2].toIntOrNull() ?: 0
            ps.ingameMinutes = hh * 60 + mm
            val minutes = ps.ingameMinutes
            // Day: 08:30..20:30 inclusive; Night: 20:40..08:20
            val dayStart = 8 * 60 + 30
            val dayEnd = 20 * 60 + 30
            ps.isDayTime = minutes in dayStart..dayEnd
        }

        // Extract island name from scoreboard first line: "MCCI: <island name>"
        val sb = client.player?.scoreboard
        val obj = sb?.getDisplayObjective(DisplaySlot.SIDEBAR)
        val displayName = obj?.displayName
        val displayNameString = displayName?.string
        if (displayNameString?.startsWith("MCCI:", true) == true) {
            val island = displayNameString.substringAfter(":").trim()
            if (island.isNotEmpty()) ps.currentCollection = island
        }

        ps.tideLines.clear()
        ps.windLines.clear()
        ps.magnetPylonBonus = 0
        if (!ps.inGrotto) {
            fun detect(line: UpgradeLine, key: String) {
                if (titles.contains(key, ignoreCase = true)) ps.tideLines.add(line)
                if (titles.contains(key.replace(" Tide", " Winds"), ignoreCase = true)) ps.windLines.add(line)
            }
            detect(UpgradeLine.STRONG, "Strong Tide")
            detect(UpgradeLine.WISE, "Wise Tide")
            detect(UpgradeLine.GLIMMERING, "Glimmering Tide")
            detect(UpgradeLine.GREEDY, "Greedy Tide")
            detect(UpgradeLine.LUCKY, "Lucky Tide")

            // Pylon: X - Ym or < 1m (server does not show seconds; sub-minute shown as "< 1m")
            ps.magnetPylonTimeLeftSeconds = 0
            val pylonRegex = Regex("(?i)Pylon:\\s*(\\d+)\\s*.?\\s*[^-]*?(?:-\\s*(?:(\\d+)\\s*m|<\\s*1m))?")
            val m = pylonRegex.find(titles)
            if (m != null) {
                ps.magnetPylonBonus = m.groups[1]?.value?.toIntOrNull() ?: 0
                val minutesGroup = m.groups[2]?.value
                if (minutesGroup != null) {
                    val mins = minutesGroup.toIntOrNull() ?: 0
                    ps.magnetPylonTimeLeftSeconds = mins * 60
                } else if (Regex("(?i)-\\s*<\\s*1m").containsMatchIn(m.value)) {
                    // Represent sub-minute as 59 seconds for UI purposes
                    ps.magnetPylonTimeLeftSeconds = 59
                }
            } else {
                ps.magnetPylonBonus = 0
            }
        }

        DialogCollection.refreshDialog("hookchances")
        DialogCollection.refreshDialog("magnetchances")
        DialogCollection.refreshDialog("chanceperks")
        DialogCollection.refreshDialog("spot")
        DialogCollection.refreshDialog("fishcollection")
    }
}


