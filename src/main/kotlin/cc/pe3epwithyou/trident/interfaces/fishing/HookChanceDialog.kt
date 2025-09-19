package cc.pe3epwithyou.trident.interfaces.fishing

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.interfaces.shared.TridentDialog
import cc.pe3epwithyou.trident.interfaces.themes.DialogTitle
import cc.pe3epwithyou.trident.interfaces.themes.TridentThemed
import cc.pe3epwithyou.trident.state.Rarity
import cc.pe3epwithyou.trident.state.FishWeightColor
import cc.pe3epwithyou.trident.state.FontCollection
import cc.pe3epwithyou.trident.state.PearlQualityColor
import cc.pe3epwithyou.trident.state.SpiritPurityColor
import cc.pe3epwithyou.trident.state.fishing.PerkStateCalculator
import cc.pe3epwithyou.trident.state.fishing.UpgradeLine
import cc.pe3epwithyou.trident.state.fishing.UpgradeType
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.mccFont
import cc.pe3epwithyou.trident.state.fishing.Augment
import com.noxcrew.sheeplib.LayoutConstants
import com.noxcrew.sheeplib.dialog.title.DialogTitleWidget
import com.noxcrew.sheeplib.layout.grid
import com.noxcrew.sheeplib.theme.Themed
import com.noxcrew.sheeplib.util.opacity
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class HookChanceDialog(x: Int, y: Int, key: String) : TridentDialog(x, y, key), Themed by TridentThemed {
    private companion object {
        private val TITLE_COLOR: Int = 0x54fcfc opacity 127
    }

    private fun getWidgetTitle(): DialogTitleWidget {
        val icon = FontCollection.get("_fonts/icon/quest_log.png")
            .withStyle(
                Style.EMPTY
                    .withShadowColor(0x0 opacity 0)
            )
        val text = Component.literal(" HOOK CHANCES".uppercase()).mccFont()
        return DialogTitle(this, icon.append(text), TITLE_COLOR)
    }

    override var title = getWidgetTitle()

    private val expanded: MutableMap<UpgradeLine, Boolean> = mutableMapOf(
        UpgradeLine.STRONG to false,
        UpgradeLine.WISE to false,
        UpgradeLine.GLIMMERING to false,
        UpgradeLine.GREEDY to false,
        UpgradeLine.LUCKY to false,
    )

    private fun hookMultiplier(points: Int): Double = 1.0 + (points * 0.1)

    private data class Cat(val name: String, val pct: Double)

    private fun hookBase(line: UpgradeLine): List<Cat> = when (line) {
        UpgradeLine.STRONG -> listOf(
            Cat("Average", 90.3), Cat("Large", 7.5), Cat("Massive", 2.0), Cat("Gargantuan", 0.2)
        )
        UpgradeLine.WISE -> listOf(
            Cat("Common", 54.8), Cat("Uncommon", 25.0), Cat("Rare", 15.0), Cat("Epic", 4.0), Cat("Legendary", 1.0), Cat("Mythic", 0.2)
        )
        UpgradeLine.GLIMMERING -> listOf(
            Cat("Rough", 94.9), Cat("Polished", 5.0), Cat("Pristine", 0.1)
        )
        UpgradeLine.GREEDY -> listOf(
            Cat("Common", 60.6), Cat("Uncommon", 29.85), Cat("Rare", 7.0), Cat("Epic", 2.0), Cat("Legendary", 0.5), Cat("Mythic", 0.05)
        )
        UpgradeLine.LUCKY -> listOf(
            Cat("Normal", 96.95), Cat("Refined", 3.0), Cat("Pure", 0.05)
        )
    }

    private fun hookTargets(line: UpgradeLine): Set<String> = when (line) {
        UpgradeLine.STRONG -> setOf("Large", "Massive", "Gargantuan")
        UpgradeLine.WISE -> setOf("Epic", "Legendary", "Mythic")
        UpgradeLine.GLIMMERING -> setOf("Polished", "Pristine")
        UpgradeLine.GREEDY -> setOf("Rare", "Epic", "Legendary", "Mythic")
        UpgradeLine.LUCKY -> setOf("Refined", "Pure")
    }

    private fun applyMultiplier(line: UpgradeLine, base: List<Cat>, targets: Set<String>, mult: Double): List<Cat> {
        if (base.isEmpty()) return base
        val hasActiveRarityRod = TridentClient.playerState.supplies.augments
            .any { it.augment == Augment.RARITY_ROD && !it.paused && ((it.usesCurrent ?: 0) > 0) }

        fun finalizeWithRarityRod(list: List<Cat>): List<Cat> {
            if (!(line == UpgradeLine.WISE && hasActiveRarityRod)) return list
            val order = list.map { it.name }
            val map = list.associate { it.name to it.pct }.toMutableMap()

            // Guarantee Uncommon+: zero Common, share its mass between Uncommon and Epic proportionally
            val common = map["Common"] ?: 0.0
            map["Common"] = 0.0
            val un = map["Uncommon"] ?: 0.0
            val ep = map["Epic"] ?: 0.0
            val ueSum = un + ep
            if (ueSum > 0.0) {
                map["Uncommon"] = un + common * (un / ueSum)
                map["Epic"] = ep + common * (ep / ueSum)
            } else {
                map["Uncommon"] = un + common
            }

            // Triple Legendary and Mythic AFTER normal calculation
            map["Legendary"] = (map["Legendary"] ?: 0.0) * 3.0
            map["Mythic"] = (map["Mythic"] ?: 0.0) * 3.0

            // Keep Rare static (do not modify from its current value)
            val rareFixed = map["Rare"] ?: 0.0

            // Normalize to 100: reduce Uncommon/Epic first, then Legendary/Mythic if needed; never touch Rare, keep Common at 0
            var sum = map.values.sum()
            if (sum > 100.0 + 1e-9) {
                val over = sum - 100.0
                // First shrink Uncommon + Epic
                val uePool = listOf("Uncommon", "Epic")
                var ueSum2 = uePool.sumOf { map[it] ?: 0.0 }
                var remainingOver = over
                if (ueSum2 > 1e-9) {
                    val ueScale = (ueSum2 - remainingOver).coerceAtLeast(0.0) / ueSum2
                    uePool.forEach { k -> map[k] = (map[k] ?: 0.0) * ueScale }
                    remainingOver -= (ueSum2 - (ueSum2 * ueScale))
                }
                if (remainingOver > 1e-6) {
                    // Then shrink Legendary + Mythic proportionally
                    val lmPool = listOf("Legendary", "Mythic")
                    val lmSum = lmPool.sumOf { map[it] ?: 0.0 }
                    if (lmSum > 1e-9) {
                        val lmScale = (lmSum - remainingOver).coerceAtLeast(0.0) / lmSum
                        lmPool.forEach { k -> map[k] = (map[k] ?: 0.0) * lmScale }
                    }
                }
                // Restore Rare to its fixed value (in case tiny numeric drift)
                map["Rare"] = rareFixed
                sum = map.values.sum()
            } else if (sum < 100.0 - 1e-9) {
                map["Uncommon"] = (map["Uncommon"] ?: 0.0) + (100.0 - sum)
            }

            return order.map { Cat(it, map[it] ?: 0.0) }
        }

        if (mult == 1.0) return finalizeWithRarityRod(base)
        val baseSum = base.sumOf { it.pct }
        val targetSet = targets.toSet()
        val targetBaseSum = base.filter { it.name in targetSet }.sumOf { it.pct }
        if (targetBaseSum == 0.0) return base
        val targetNewSum = targetBaseSum * mult

        if (targetNewSum >= baseSum - 1e-9) {
            return finalizeWithRarityRod(base.map { c ->
                val pct = if (c.name in targetSet) (c.pct / targetBaseSum) * baseSum else 0.0
                Cat(c.name, pct)
            })
        }

        if (line == UpgradeLine.WISE) {
            val pctMap = base.associate { it.name to it.pct }.toMutableMap()
            targetSet.forEach { t -> pctMap[t]?.let { pctMap[t] = it * mult } }
            var delta = targetNewSum - targetBaseSum
            val commonBase = pctMap["Common"] ?: 0.0
            val takeCommon = kotlin.math.min(commonBase, delta)
            pctMap["Common"] = commonBase - takeCommon
            delta -= takeCommon
            if (delta > 1e-9) {
                val unBase = pctMap["Uncommon"] ?: 0.0
                val takeUn = kotlin.math.min(unBase, delta)
                pctMap["Uncommon"] = unBase - takeUn
                delta -= takeUn
            }
            if (delta > 1e-9) {
                val currentTargetsSum = targetSet.sumOf { pctMap[it] ?: 0.0 }
                val scale = if (currentTargetsSum <= 0.0) 0.0 else (currentTargetsSum - delta) / currentTargetsSum
                targetSet.forEach { t -> pctMap[t]?.let { pctMap[t] = it * scale } }
            }
            return finalizeWithRarityRod(base.map { c -> Cat(c.name, pctMap[c.name] ?: 0.0) })
        }

        val nonTargetBaseSum = baseSum - targetBaseSum
        val nonTargetNewSum = baseSum - targetNewSum
        val nonTargetScale = if (nonTargetBaseSum <= 0.0) 0.0 else nonTargetNewSum / nonTargetBaseSum

        return finalizeWithRarityRod(base.map { c ->
            val pct = if (c.name in targetSet) c.pct * mult else c.pct * nonTargetScale
            Cat(c.name, pct)
        })
    }

    private fun rarityColorFor(name: String): Int? = when (name.lowercase()) {
        "common" -> Rarity.COMMON.color
        "uncommon" -> Rarity.UNCOMMON.color
        "rare" -> Rarity.RARE.color
        "epic" -> Rarity.EPIC.color
        "legendary" -> Rarity.LEGENDARY.color
        "mythic" -> Rarity.MYTHIC.color
        // Fish weight (Strong)
        "average" -> FishWeightColor.AVERAGE.color
        "large" -> FishWeightColor.LARGE.color
        "massive" -> FishWeightColor.MASSIVE.color
        "gargantuan" -> FishWeightColor.GARGANTUAN.color
        // Pearls (Glimmering)
        "rough" -> PearlQualityColor.ROUGH.color
        "polished" -> PearlQualityColor.POLISHED.color
        "pristine" -> PearlQualityColor.PRISTINE.color
        // Spirits (Lucky)
        "normal" -> SpiritPurityColor.NORMAL.color
        "refined" -> SpiritPurityColor.REFINED.color
        "pure" -> SpiritPurityColor.PURE.color
        else -> null
    }

    private fun renderChancesRow(label: String, cats: List<Cat>, accent: ChatFormatting, font: net.minecraft.client.gui.Font): StringWidget {
        var comp: net.minecraft.network.chat.MutableComponent = Component.literal(label).mccFont().withStyle(ChatFormatting.GRAY)
            .append(Component.literal(" ").mccFont())

        cats.forEachIndexed { idx, cat ->
            if (idx > 0) {
                comp = comp.append(Component.literal(" | ").mccFont().withStyle(ChatFormatting.GRAY))
            }
            val abbrev = cat.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val nameColored = rarityColorFor(cat.name)?.let { color ->
                Component.literal(abbrev).mccFont().withColor(color)
            } ?: Component.literal(abbrev).mccFont().withStyle(accent)

            val pctComp = Component.literal(" ${"""%.2f""".format(cat.pct)}%").mccFont().withStyle(accent)
            comp = comp.append(nameColored).append(pctComp)
        }

        return StringWidget(comp, font)
    }

    override fun layout(): GridLayout = grid {
        val font = Minecraft.getInstance().font
        TridentClient.playerState.perkState = PerkStateCalculator.recompute(
            TridentClient.playerState
        )
        val ps = TridentClient.playerState.perkState

        var row = 0
        StringWidget(Component.literal("HOOKS").mccFont().withStyle(ChatFormatting.AQUA), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)

        UpgradeLine.entries.forEach { line ->
            val basePts = ps.totals[line]?.get(UpgradeType.HOOK)?.total ?: 0
            val spot = TridentClient.playerState.spot
            val tides = TridentClient.playerState.tideLines
            val spotPct = if (spot.hasSpot) (spot.hookPercents[line] ?: 0.0) else 0.0
            val tidePct = if (tides.contains(line)) 20.0 else 0.0
            val combinedPct = spotPct + tidePct
            val effectivePts = basePts * (1.0 + combinedPct / 100.0)
            val mult = 1.0 + (effectivePts * 0.1)
            val deltaSpot = basePts * (spotPct / 100.0)
            val deltaTide = basePts * (tidePct / 100.0)
            val lineLabel = line.name.lowercase().replaceFirstChar { it.uppercase() }
            val caret = if (expanded[line] == true) "v" else ">"
            val condensedCalc = Component.literal(" ${basePts}*(${"""%.0f""".format(spotPct)}%+${"""%.0f""".format(tidePct)}%)")
                .mccFont().withStyle(ChatFormatting.GRAY)
            val headerBase = Component.literal("$caret ").mccFont()
                .append(Component.literal(lineLabel).mccFont().withColor(
                    when(line){
                        UpgradeLine.STRONG -> FishWeightColor.baseColor
                        UpgradeLine.WISE -> Rarity.RARE.color
                        UpgradeLine.GLIMMERING -> PearlQualityColor.baseColor
                        UpgradeLine.GREEDY -> Rarity.LEGENDARY.color
                        UpgradeLine.LUCKY -> SpiritPurityColor.baseColor
                    }
                )).append(Component.literal(": ").mccFont())
                .append(Component.literal("x${"""%.1f""".format(mult)}").mccFont().withStyle(ChatFormatting.AQUA))
            val headerCollapsed = headerBase.copy().append(condensedCalc)
            val headerExpanded = headerBase

            val buttonLabel = if (expanded[line] == true) headerExpanded else headerCollapsed

            Button.builder(buttonLabel) {
                expanded[line] = !(expanded[line] ?: false)
                this@HookChanceDialog.refresh()
            }.bounds(0, 0, 180, 12).build().at(row++, 0, settings = LayoutConstants.LEFT)

            if (expanded[line] == true) {
                val baseCats = hookBase(line)
                val boosted = applyMultiplier(line, baseCats, hookTargets(line), mult)
                // Expanded detailed calculation
                val detail = Component.literal("pts ").mccFont().withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("${basePts}").mccFont().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" + ").mccFont().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("${"""%.2f""".format(spotPct)}% ").mccFont().withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal("(+${"""%.2f""".format(deltaSpot)}) ").mccFont().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("+ ").mccFont().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("${"""%.2f""".format(tidePct)}% ").mccFont().withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal("(+${"""%.2f""".format(deltaTide)}) ").mccFont().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("= ${"""%.2f""".format(effectivePts)}").mccFont().withStyle(ChatFormatting.GRAY))
                StringWidget(detail, font).at(row++, 0, settings = LayoutConstants.LEFT)

                val baseRow = renderChancesRow("Base:", baseCats, ChatFormatting.GRAY, font)
                baseRow.at(row++, 0, settings = LayoutConstants.LEFT)
                val realRow = renderChancesRow("Real:", boosted, ChatFormatting.AQUA, font)
                realRow.at(row++, 0, settings = LayoutConstants.LEFT)
            }
            // extra spacing row between types
            row++
        }
    }

    override fun refresh() {
        title = getWidgetTitle()
        super.refresh()
    }
}


