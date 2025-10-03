package cc.pe3epwithyou.trident.utils

import cc.pe3epwithyou.trident.state.FontCollection
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object TradeableDetector {
    private val CONFIRM_ICON_PATHS = listOf(
        "_fonts/icon/tooltips/tradeable.png"
    )
    private val INFER_ICON_PATHS = listOf(
        "_fonts/icon/tooltips/limited.png",
        "_fonts/icon/tooltips/collector.png"
    )

    private fun charsForPaths(paths: List<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val set = mutableSetOf<String>()
        FontCollection.collection.forEach { (icon, ch) ->
            val p = icon.path
            if (paths.any { sub -> matchesPath(p, sub) }) set.add(ch)
        }
        return set
    }

    private fun matchesPath(loc: ResourceLocation, substring: String): Boolean {
        return loc.namespace.contains("mcc", true) && loc.path.contains(substring, true)
    }

    private val confirmChars: Set<String> by lazy { charsForPaths(CONFIRM_ICON_PATHS) }
    private val inferChars: Set<String> by lazy { charsForPaths(INFER_ICON_PATHS) }

    fun isTradeable(stack: ItemStack, lore: List<Component>): Boolean {
        val name = stack.hoverName.string
        if (name.equals("Coins", true)) return false

        val flatChars = lore.joinToString(separator = "") { it.string }
        if (confirmChars.any { flatChars.contains(it) }) return true
        if (inferChars.any { flatChars.contains(it) }) return true

        return false
    }
}


