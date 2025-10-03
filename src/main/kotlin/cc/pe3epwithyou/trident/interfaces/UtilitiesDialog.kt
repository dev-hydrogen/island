package cc.pe3epwithyou.trident.interfaces

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.config.Config
import cc.pe3epwithyou.trident.interfaces.shared.TridentDialog
import cc.pe3epwithyou.trident.interfaces.themes.DialogTitle
import cc.pe3epwithyou.trident.interfaces.themes.TridentThemed
import cc.pe3epwithyou.trident.net.IslandExchangeService
import cc.pe3epwithyou.trident.feature.fishing.FishCollectionService
import cc.pe3epwithyou.trident.state.FontCollection
import cc.pe3epwithyou.trident.state.Game
import cc.pe3epwithyou.trident.state.MCCIState
import cc.pe3epwithyou.trident.utils.ChatUtils
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.mccFont
import com.noxcrew.sheeplib.LayoutConstants
import com.noxcrew.sheeplib.dialog.title.DialogTitleWidget
import com.noxcrew.sheeplib.layout.grid
import com.noxcrew.sheeplib.theme.Themed
import com.noxcrew.sheeplib.util.opacity
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Locale

class UtilitiesDialog(x: Int, y: Int, key: String) : TridentDialog(x, y, key), Themed by TridentThemed {
    private companion object {
        private val TITLE_COLOR: Int = 0x54fcfc opacity 127
        private val ISLANDS: List<String> = listOf(
            "Verdant Woods",
            "Floral Forest",
            "Dark Grove",
            "Tropical Overgrowth",
            "Coral Shores",
            "Twisted Swamp",
            "Ancient Sands",
            "Blazing Canyon",
            "Ashen Wastes",
        )
    }

    // Track dynamic state updaters so refresh() can re-evaluate active/tooltip
    private val controlsToRefresh: MutableList<() -> Unit> = mutableListOf()
    // Generic per-button cooldown map: key -> epoch millis when cooldown ends
    private val cooldowns: MutableMap<String, Long> = mutableMapOf()

    private fun titleCaseWords(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.lowercase(Locale.getDefault()).split(" ")
            .joinToString(" ") { w ->
                if (w.isEmpty()) "" else w.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
    }

    private fun buildAnnouncePylonMessage(): String? {
        val player = Minecraft.getInstance().player ?: return null
        val x = player.blockX
        val y = player.blockY
        val z = player.blockZ
        val islandRaw = TridentClient.playerState.currentCollection
        val indexTag = getIslandIndexTag(islandRaw)
        val islandDisplayName = titleCaseWords(islandRaw)
        val islandDisplay = if (islandDisplayName.isNotBlank() && indexTag.isNotEmpty()) "(${islandDisplayName})" else ""
        val base = "GCS Pylon: X:$x Y:$y Z:$z"
        val secs = TridentClient.playerState.magnetPylonTimeLeftSeconds
        val timeTag = if (secs > 0) {
            val t = if (secs >= 60) "${secs / 60}m" else "<1m"
            "| Time left $t"
        } else ""
        return listOf(base, indexTag, islandDisplay, timeTag).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun getCooldownLeftMs(key: String): Long {
        val until = cooldowns[key] ?: 0L
        val left = until - System.currentTimeMillis()
        return if (left > 0L) left else 0L
    }

    private fun startCooldown(key: String, durationMs: Long) {
        cooldowns[key] = System.currentTimeMillis() + durationMs
    }

    private fun getIslandIndexTag(islandName: String?): String {
        if (islandName.isNullOrBlank()) return ""
        val idx = ISLANDS.indexOfFirst { base -> islandName.contains(base, ignoreCase = true) }
        return if (idx >= 0) "I${idx + 1}" else ""
    }

    private fun getWidgetTitle(): DialogTitleWidget {
        val icon = FontCollection.get("_fonts/icon/quest_log.png")
            .withStyle(
                Style.EMPTY
                    .withShadowColor(0x0 opacity 0)
            )
        val text = Component.literal(" UTILITIES").mccFont()
        return DialogTitle(this, icon.append(text), TITLE_COLOR)
    }

    override var title = getWidgetTitle()

    private fun conditionalButton(
        label: String,
        width: Int = 140,
        cooldownKey: String? = null,
        cooldownDurationMs: Long = 0L,
        shouldEnable: () -> Pair<Boolean, String?>,
        tooltipWhenEnabled: () -> String,
        onClick: () -> Unit
    ): Button {
        val btn = Button.builder(Component.literal(label).mccFont()) {
            val (baseEnabled, _) = shouldEnable()
            val inCooldown = cooldownKey != null && getCooldownLeftMs(cooldownKey) > 0
            if (!(baseEnabled && !inCooldown)) return@builder
            onClick()
            if (cooldownKey != null && cooldownDurationMs > 0L) {
                startCooldown(cooldownKey, cooldownDurationMs)
                this@UtilitiesDialog.refresh()
            }
        }.bounds(0, 0, width, 12).build()

        val applyState: () -> Unit = {
            val (baseEnabled, baseReason) = shouldEnable()
            val cooldownLeftMs = cooldownKey?.let { getCooldownLeftMs(it) } ?: 0L
            val inCooldown = cooldownLeftMs > 0L
            val enabled = baseEnabled && !inCooldown
            val reason = when {
                !baseEnabled -> baseReason
                inCooldown -> "Cooldown ${((cooldownLeftMs + 999) / 1000)}s"
                else -> null
            }
            btn.active = enabled
            val tipText = if (!enabled) {
                val base = if (reason.isNullOrBlank()) "Disabled" else "Disabled: $reason"
                base
            } else {
                tooltipWhenEnabled()
            }
            btn.setTooltip(Tooltip.create(Component.literal(tipText).withStyle(ChatFormatting.GRAY)))
        }

        applyState()
        controlsToRefresh.add(applyState)
        return btn
    }

    private fun apiButton(label: String, onClick: () -> Unit): Button {
        val cooldownKey = "api_" + label.lowercase().replace("\\s+".toRegex(), "_")
        val tooltip = when {
            label.contains("fish", ignoreCase = true) -> "Fetches your fish collection via the API"
            label.contains("exchange", ignoreCase = true) -> "Fetches Island Exchange listings via the API"
            else -> "Calls the Island++ API"
        }
        return conditionalButton(
            label = label,
            width = 140,
            cooldownKey = cooldownKey,
            cooldownDurationMs = 5_000L,
            shouldEnable = {
                val hasKey = Config.Api.apiKey.isNotBlank()
                val reason = if (hasKey) null else "set API Key in Config -> API"
                Pair(hasKey, reason)
            },
            tooltipWhenEnabled = { tooltip },
            onClick = onClick
        )
    }

    override fun layout(): GridLayout = grid {
        val font = Minecraft.getInstance().font

        var row = 0

        // Clear previous dynamic updaters when rebuilding layout
        controlsToRefresh.clear()

        // Chat category
        StringWidget(Component.literal("CHAT").mccFont().withStyle(ChatFormatting.AQUA), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)

        val announceBtn = conditionalButton(
            label = "Announce Pylon",
            width = 120,
            cooldownKey = "announce_pylon",
            cooldownDurationMs = 10_000L,
            shouldEnable = {
                val notFishing = MCCIState.game != Game.FISHING
                val inGrotto = TridentClient.playerState.inGrotto
                val havePylon = TridentClient.playerState.magnetPylonBonus > 0
                val enabled = !inGrotto && !notFishing && havePylon
                val reason = when {
                    inGrotto -> "Grotto"
                    notFishing -> "Not in Fishing"
                    !havePylon -> "No Pylon"
                    else -> null
                }
                Pair(enabled, reason)
            },
            tooltipWhenEnabled = {
                val msg = buildAnnouncePylonMessage()
                msg?.let { "Announces to chat: $it" } ?: "Announces your current Pylon location in chat"
            }
        ) {
            val msg = buildAnnouncePylonMessage()
            if (msg == null) {
                ChatUtils.sendMessage(Component.literal("Player not available").withStyle(ChatFormatting.RED))
            } else {
                val connection = Minecraft.getInstance().connection
                if (connection != null) {
                    connection.sendChat(msg)
                }
            }
        }
        announceBtn.at(row++, 0, settings = LayoutConstants.LEFT)

        // Update the button label with pylon time left (Xm/Xs). Hidden if no time.
        val updateAnnounceLabel: () -> Unit = {
            val pylonSecs = TridentClient.playerState.magnetPylonTimeLeftSeconds
            val cooldownLeftMs = getCooldownLeftMs("announce_pylon")
            val cooldownSecs = ((cooldownLeftMs + 999) / 1000).toInt()
            val suffix = if (cooldownSecs > 0) {
                " (cd ${cooldownSecs}s)"
            } else if (pylonSecs > 0) {
                if (pylonSecs >= 60) " (${pylonSecs / 60}m)" else " (${pylonSecs}s)"
            } else ""
            announceBtn.setMessage(Component.literal("Announce Pylon$suffix").mccFont())
        }
        updateAnnounceLabel()
        controlsToRefresh.add(updateAnnounceLabel)

        // spacer
        row += 1

        // API category
        StringWidget(Component.literal("API").mccFont().withStyle(ChatFormatting.AQUA), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)

        // Fetch Fish Data
        apiButton("Fetch Fish Data") {
            val uuid = Minecraft.getInstance().player?.uuid?.toString()
            if (uuid == null) {
                ChatUtils.sendMessage(Component.literal("Player UUID unavailable").withStyle(ChatFormatting.RED))
            } else {
                FishCollectionService.refreshForPlayer(uuid)
                ChatUtils.sendMessage(Component.literal("Requested fish data fetch").withStyle(ChatFormatting.AQUA))
            }
        }.at(row++, 0, settings = LayoutConstants.LEFT)

        // Fetch Exchange Data
        apiButton("Fetch Exchange Data") {
            IslandExchangeService.refreshNow(reason = "manual")
            ChatUtils.sendMessage(Component.literal("Requested Island Exchange refresh").withStyle(ChatFormatting.AQUA))
        }.at(row++, 0, settings = LayoutConstants.LEFT)
    }

    override fun refresh() {
        title = getWidgetTitle()
        super.refresh()
        // Re-evaluate dynamic controls state on refresh
        controlsToRefresh.forEach { it.invoke() }
    }
}


