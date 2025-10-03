package cc.pe3epwithyou.trident.client.listeners

import cc.pe3epwithyou.trident.config.Config
import cc.pe3epwithyou.trident.net.IslandExchangeService
import cc.pe3epwithyou.trident.state.MCCIState
import cc.pe3epwithyou.trident.utils.TradeableDetector
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import java.text.NumberFormat
import java.util.Locale

object TooltipExchangeListener {
    private val targetMenus = listOf(
        "INFINIBAG",
        "MY PROFILE",
        "INFINIVAULT",
        "ISLAND EXCHANGE"
    )

    private fun isTargetMenuOpen(): Boolean {
        val screen = Minecraft.getInstance().screen
        if (screen !is ContainerScreen) return false
        val title = screen.title.string.uppercase()
        return targetMenus.any { title.contains(it) }
    }

    fun register() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, context, flag, lines ->
            if (!MCCIState.isOnIsland()) return@ItemTooltipCallback
            if (!Config.Exchange.tooltipEnabled) return@ItemTooltipCallback
            if (Config.Api.apiKey.isBlank()) return@ItemTooltipCallback
            if (!isTargetMenuOpen()) return@ItemTooltipCallback
            if (stack.isEmpty) return@ItemTooltipCallback

            val lore = lines.toList()
            if (!TradeableDetector.isTradeable(stack, lore)) return@ItemTooltipCallback

            val name = stack.hoverName.string
            if (name.equals("Coins", true)) return@ItemTooltipCallback

            val stats = IslandExchangeService.computePriceStatsForName(name)
            val lowest = formatPrice(stats.lowestPerUnit)
            val avg24h = formatPrice(stats.avgSoldPerUnit24h)
            
            lines.add(Component.literal(""))
            lines.add(Component.literal("Lowest listed price: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(lowest).withStyle(ChatFormatting.AQUA)))
            lines.add(Component.literal("Average sold price (24h): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(avg24h).withStyle(ChatFormatting.AQUA)))
        })
    }

    private fun formatPrice(value: Double?): String {
        if (value == null) return "NO DATA"
        val v = kotlin.math.round(value).toLong()
        return NumberFormat.getIntegerInstance(Locale.US).format(v)
    }
}


