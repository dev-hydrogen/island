package cc.pe3epwithyou.trident.interfaces.fishing

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.interfaces.shared.TridentDialog
import cc.pe3epwithyou.trident.interfaces.themes.DialogTitle
import cc.pe3epwithyou.trident.interfaces.themes.TridentThemed
import cc.pe3epwithyou.trident.state.fishing.Augment
import cc.pe3epwithyou.trident.state.fishing.UpgradeLine
import cc.pe3epwithyou.trident.state.fishing.UpgradeType
import cc.pe3epwithyou.trident.state.FishWeightColor
import cc.pe3epwithyou.trident.state.PearlQualityColor
import cc.pe3epwithyou.trident.state.SpiritPurityColor
import cc.pe3epwithyou.trident.state.TreasureRarityColor
import cc.pe3epwithyou.trident.state.FishRarityColor
import cc.pe3epwithyou.trident.state.FontCollection
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.mccFont
import com.noxcrew.sheeplib.LayoutConstants
import com.noxcrew.sheeplib.dialog.title.DialogTitleWidget
import com.noxcrew.sheeplib.layout.grid
import com.noxcrew.sheeplib.theme.Themed
import com.noxcrew.sheeplib.util.opacity
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class SpotDialog(x: Int, y: Int, key: String) : TridentDialog(x, y, key), Themed by TridentThemed {
    private companion object {
        private val TITLE_COLOR: Int = 0x54fcfc opacity 127
    }

    private fun getWidgetTitle(): DialogTitleWidget {
        val icon = FontCollection.get("_fonts/icon/quest_log.png")
            .withStyle(
                Style.EMPTY
                    .withShadowColor(0x0 opacity 0)
            )
        val text = Component.literal(" SPOT".uppercase()).mccFont()
        return DialogTitle(this, icon.append(text), TITLE_COLOR)
    }

    override var title = getWidgetTitle()

    private var expectedExpanded: Boolean = false

    override fun layout(): GridLayout = grid {
        val font = Minecraft.getInstance().font
        val spot = TridentClient.playerState.spot

        var row = 0
        val status = if (spot.hasSpot) Component.literal("ACTIVE").mccFont().withStyle(ChatFormatting.AQUA) else Component.literal("NONE").mccFont().withStyle(ChatFormatting.GRAY)
        StringWidget(Component.literal("SPOT").mccFont().withStyle(ChatFormatting.AQUA), font).at(row++, 0, settings = LayoutConstants.LEFT)
        StringWidget(Component.literal("Status: ").mccFont().append(status), font).at(row++, 0, settings = LayoutConstants.LEFT)

        if (!spot.hasSpot) return@grid

        fun lineLabel(l: UpgradeLine): String = l.name.lowercase().replaceFirstChar { it.uppercase() }

        // Stock information
        spot.stockLabel?.let { label ->
            // Effective decrease probability = base 40% reduced by Graceful Rod trigger probability
            // Graceful Rod total chance (from upgrades + line/supplies + augments + overclocks + equipment)
            val gracefulPts = (TridentClient.playerState.perkState.totals[UpgradeLine.GLIMMERING]?.get(UpgradeType.ROD)?.total
                ?: 0).coerceAtLeast(0)
            val gracefulPct = gracefulPts / 100.0
            // Compute non-junk probability (Fish/Pearl/Treasure/Spirit)
            val ps = TridentClient.playerState.perkState
            val winds = TridentClient.playerState.windLines
            val isGrotto = TridentClient.playerState.inGrotto
            val augmentList = TridentClient.playerState.supplies.augments.filterNot { it.paused }.map { it.augment }
            val hasElusiveLure = augmentList.any { it == cc.pe3epwithyou.trident.state.fishing.Augment.ELUSIVE_LURE || it == cc.pe3epwithyou.trident.state.fishing.Augment.ELUSIVE_ULTRALURE }
            val pearlLureActive = augmentList.any { it == cc.pe3epwithyou.trident.state.fishing.Augment.PEARL_LURE || it == cc.pe3epwithyou.trident.state.fishing.Augment.PEARL_ULTRALURE }
            val treasureLureActive = augmentList.any { it == cc.pe3epwithyou.trident.state.fishing.Augment.TREASURE_LURE || it == cc.pe3epwithyou.trident.state.fishing.Augment.TREASURE_ULTRALURE }
            val spiritLureActive = augmentList.any { it == cc.pe3epwithyou.trident.state.fishing.Augment.SPIRIT_LURE || it == cc.pe3epwithyou.trident.state.fishing.Augment.SPIRIT_ULTRALURE }

            val ptsStrong = ps.totals[UpgradeLine.STRONG]?.get(UpgradeType.CHANCE)?.total ?: 0
            val ptsWise = ps.totals[UpgradeLine.WISE]?.get(UpgradeType.CHANCE)?.total ?: 0
            val ptsGlim = ps.totals[UpgradeLine.GLIMMERING]?.get(UpgradeType.CHANCE)?.total ?: 0
            val ptsGreedy = ps.totals[UpgradeLine.GREEDY]?.get(UpgradeType.CHANCE)?.total ?: 0
            val ptsLucky = ps.totals[UpgradeLine.LUCKY]?.get(UpgradeType.CHANCE)?.total ?: 0

            val fishBase = 40.0
            val fishSpot100 = spot.hasSpot && spot.fishChanceBonusPercent >= 100.0
            val guaranteedType: String? = when {
                spot.hasSpot && spot.pearlChanceBonusPercent >= 100.0 -> "Pearl"
                spot.hasSpot && spot.treasureChanceBonusPercent >= 100.0 -> "Treasure"
                spot.hasSpot && spot.spiritChanceBonusPercent >= 100.0 -> "Spirit"
                (!isGrotto && pearlLureActive) -> "Pearl"
                (!isGrotto && treasureLureActive) -> "Treasure"
                (!isGrotto && spiritLureActive) -> "Spirit"
                else -> null
            }

            var pFish = when {
                guaranteedType != null -> 0.0
                fishSpot100 -> 100.0
                else -> fishBase
            }
            var pPearl = 5.0 + ptsGlim * 0.5 + (if (spot.hasSpot) spot.pearlChanceBonusPercent else 0.0) + (if (winds.contains(UpgradeLine.GLIMMERING)) 5.0 else 0.0)
            var pTreasure = 1.0 + ptsGreedy * 0.1 + (if (spot.hasSpot) spot.treasureChanceBonusPercent else 0.0) + (if (winds.contains(UpgradeLine.GREEDY)) 10.0 else 0.0)
            var pSpirit = 2.0 + ptsLucky * 0.2 + (if (spot.hasSpot) spot.spiritChanceBonusPercent else 0.0) + (if (winds.contains(UpgradeLine.LUCKY)) 5.0 else 0.0)
            val pElusiveBase = (ptsStrong * 0.5 + (if (spot.hasSpot) spot.elusiveChanceBonusPercent else 0.0) + (if (winds.contains(UpgradeLine.STRONG)) 5.0 else 0.0) + (if (hasElusiveLure) 100.0 else 0.0)).coerceAtMost(100.0)

            if (guaranteedType != null) {
                pFish = 0.0
                pPearl = if (guaranteedType == "Pearl") 100.0 else 0.0
                pTreasure = if (guaranteedType == "Treasure") 100.0 else 0.0
                pSpirit = if (guaranteedType == "Spirit") 100.0 else 0.0
            } else if (fishSpot100) {
                pPearl = 0.0; pTreasure = 0.0; pSpirit = 0.0
            }

            val pNonJunk = (pFish + pPearl + pTreasure + pSpirit).coerceAtMost(100.0) / 100.0
            val pJunk = 1.0 - pNonJunk
            val effectiveDecreaseP = 0.40 * (1.0 - gracefulPct) * pNonJunk
            val expectedPerDecrease = if (effectiveDecreaseP > 0) 1.0 / effectiveDecreaseP else Double.POSITIVE_INFINITY
            val observed = spot.observedNonJunkCatches
            val decreases = spot.observedStockDecreases
            val expectedDecreases = observed * effectiveDecreaseP
            val trend = when {
                decreases > expectedDecreases + 1e-6 -> Component.literal("(decreasing faster than avg)").mccFont().withStyle(ChatFormatting.RED)
                decreases < expectedDecreases - 1e-6 -> Component.literal("(decreasing slower than avg)").mccFont().withStyle(ChatFormatting.GREEN)
                else -> Component.literal("(on average)").mccFont().withStyle(ChatFormatting.GRAY)
            }
            StringWidget(Component.literal("Stock: ").mccFont()
                .append(Component.literal(label).mccFont().withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  ").mccFont())
                .append(trend), font).at(row++, 0, settings = LayoutConstants.LEFT)

            // Expected catches remaining estimator by bucket (rough heuristic)
            val bucketSize = when (label.lowercase()) {
                "plentiful" -> 5
                "very high" -> 4
                "high" -> 3
                "medium" -> 2
                "low" -> 1
                else -> 1
            }
            val expectedCatchesPerBucket = expectedPerDecrease * bucketSize
            val expComp = Component.literal("Expected catches left: ")
                .mccFont()
                .append(Component.literal("~${"""%.1f""".format(expectedCatchesPerBucket)}").mccFont().withStyle(ChatFormatting.AQUA))
            StringWidget(expComp, font).at(row++, 0, settings = LayoutConstants.LEFT)

            // Expected return dropdown
            val buttonLabel = if (expectedExpanded)
                Component.literal("v Expected return").mccFont()
            else
                Component.literal("> Expected return").mccFont()
            net.minecraft.client.gui.components.Button.builder(buttonLabel) {
                expectedExpanded = !expectedExpanded
                this@SpotDialog.refresh()
            }.bounds(0, 0, 120, 12).build().at(row++, 0, settings = LayoutConstants.LEFT)

            if (expectedExpanded) {
                val catchesLeft = expectedCatchesPerBucket
                val pElusiveEff = (pElusiveBase / 100.0) * (pFish / 100.0) * 100.0

                // Magnets expected extra items
                val pylon = TridentClient.playerState.magnetPylonBonus
                fun effMagPct(line: UpgradeLine, label: String): Double {
                    val basePts = ps.totals[line]?.get(UpgradeType.MAGNET)?.total ?: 0
                    val pts = basePts + pylon
                    val spotPct = if (spot.hasSpot) (spot.magnetPercents[line] ?: 0.0) else 0.0
                    val tidePct = if (TridentClient.playerState.tideLines.contains(line)) 20.0 else 0.0
                    val effPts = pts * (1.0 + (spotPct + tidePct) / 100.0)
                    return if (label == "Fish") (effPts * 10) else (effPts * 5)
                }

                val expFish = catchesLeft * (pFish / 100.0)
                val expPearl = catchesLeft * (pPearl / 100.0)
                val expTreasure = catchesLeft * (pTreasure / 100.0)
                val expSpirit = catchesLeft * (pSpirit / 100.0)
                val expElusive = catchesLeft * (pElusiveEff / 100.0)
                val expJunk = catchesLeft * pJunk

                val magFish = if (pFish > 0.0) catchesLeft * (effMagPct(UpgradeLine.WISE, "Fish") / 100.0) else 0.0
                val magPearl = catchesLeft * (effMagPct(UpgradeLine.GLIMMERING, "Pearl") / 100.0)
                val magTreasure = catchesLeft * (effMagPct(UpgradeLine.GREEDY, "Treasure") / 100.0)
                val magSpirit = catchesLeft * (effMagPct(UpgradeLine.LUCKY, "Spirit") / 100.0)
                val magXP = catchesLeft * (effMagPct(UpgradeLine.STRONG, "XP") / 100.0)

                fun row(lbl: String, base: Double, @Suppress("UNUSED_PARAMETER") extra: Double, color: ChatFormatting = ChatFormatting.AQUA) {
                    val comp = Component.literal("$lbl: ")
                        .mccFont()
                        .append(Component.literal("${"""%.2f""".format(base)}").mccFont().withStyle(color))
                    StringWidget(comp, font).at(row++, 0, settings = LayoutConstants.LEFT)
                }
                StringWidget(Component.literal("Expected catches (~${"""%.1f""".format(catchesLeft)})").mccFont().withStyle(ChatFormatting.GRAY), font)
                    .at(row++, 0, settings = LayoutConstants.LEFT)
                val showFishRow = fishSpot100 || (guaranteedType == null && (expFish > 0.0 || magFish > 0.0))
                val showPearlRow = (guaranteedType == "Pearl") || (!fishSpot100 && guaranteedType == null && (expPearl > 0.0 || magPearl > 0.0))
                val showTreasureRow = (guaranteedType == "Treasure") || (!fishSpot100 && guaranteedType == null && (expTreasure > 0.0 || magTreasure > 0.0))
                val showSpiritRow = (guaranteedType == "Spirit") || (!fishSpot100 && guaranteedType == null && (expSpirit > 0.0 || magSpirit > 0.0))

                if (showFishRow) row("Fish", expFish, magFish)
                if (showPearlRow) row("Pearls", expPearl, magPearl)
                if (showTreasureRow) row("Treasure", expTreasure, magTreasure)
                if (showSpiritRow) row("Spirits", expSpirit, magSpirit)
                if (showFishRow && expElusive > 0.0) {
                    StringWidget(Component.literal("Elusive Fish: ").mccFont()
                        .append(Component.literal("${"""%.2f""".format(expElusive)}").mccFont().withStyle(ChatFormatting.RED)), font)
                        .at(row++, 0, settings = LayoutConstants.LEFT)
                }
                if (expJunk > 0.0 && guaranteedType == null && !fishSpot100) row("Junk", expJunk, 0.0, ChatFormatting.GRAY)

                // Hook-affected expected type breakdowns
                fun hookBase(line: UpgradeLine): List<Pair<String, Double>> = when (line) {
                    UpgradeLine.STRONG -> listOf(
                        "Average" to 90.3, "Large" to 7.5, "Massive" to 2.0, "Gargantuan" to 0.2
                    )
                    UpgradeLine.WISE -> listOf(
                        "Common" to 54.8, "Uncommon" to 25.0, "Rare" to 15.0, "Epic" to 4.0, "Legendary" to 1.0, "Mythic" to 0.2
                    )
                    UpgradeLine.GLIMMERING -> listOf(
                        "Rough" to 94.9, "Polished" to 5.0, "Pristine" to 0.1
                    )
                    UpgradeLine.GREEDY -> listOf(
                        "Common" to 60.6, "Uncommon" to 29.85, "Rare" to 7.0, "Epic" to 2.0, "Legendary" to 0.5, "Mythic" to 0.05
                    )
                    UpgradeLine.LUCKY -> listOf(
                        "Normal" to 96.95, "Refined" to 3.0, "Pure" to 0.05
                    )
                }
                fun hookTargets(line: UpgradeLine): Set<String> = when (line) {
                    UpgradeLine.STRONG -> setOf("Large", "Massive", "Gargantuan")
                    UpgradeLine.WISE -> setOf("Epic", "Legendary", "Mythic")
                    UpgradeLine.GLIMMERING -> setOf("Polished", "Pristine")
                    UpgradeLine.GREEDY -> setOf("Rare", "Epic", "Legendary", "Mythic")
                    UpgradeLine.LUCKY -> setOf("Refined", "Pure")
                }
                fun computeHookMult(line: UpgradeLine): Double {
                    val basePts = ps.totals[line]?.get(UpgradeType.HOOK)?.total ?: 0
                    val spotPct = if (spot.hasSpot) (spot.hookPercents[line] ?: 0.0) else 0.0
                    val tidePct = if (TridentClient.playerState.tideLines.contains(line)) 20.0 else 0.0
                    val effectivePts = basePts * (1.0 + (spotPct + tidePct) / 100.0)
                    return 1.0 + (effectivePts * 0.1)
                }
                fun rarityRodActive(): Boolean = TridentClient.playerState.supplies.augments.any { a ->
                    a.augment == Augment.RARITY_ROD && !a.paused && ((a.usesCurrent ?: 0) > 0)
                }
                fun applyHookMultiplier(line: UpgradeLine, base: List<Pair<String, Double>>, targets: Set<String>, mult: Double): List<Pair<String, Double>> {
                    if (base.isEmpty()) return base
                    val baseSum = base.sumOf { it.second }
                    val targetBase = base.filter { it.first in targets }.sumOf { it.second }
                    var map = base.toMap().toMutableMap()
                    if (mult != 1.0 && targetBase > 0.0) {
                        val targetNew = targetBase * mult
                        if (targetNew >= baseSum - 1e-9) {
                            map = base.associate { it.first to if (it.first in targets) (it.second / targetBase) * baseSum else 0.0 }.toMutableMap()
                        } else if (line == UpgradeLine.WISE) {
                            // Special Wise: take from Common first, then Uncommon
                            targets.forEach { t -> map[t]?.let { map[t] = it * mult } }
                            var delta = targetNew - targetBase
                            val commonBase = map["Common"] ?: 0.0
                            val takeCommon = kotlin.math.min(commonBase, delta)
                            map["Common"] = commonBase - takeCommon; delta -= takeCommon
                            if (delta > 1e-9) {
                                val unBase = map["Uncommon"] ?: 0.0
                                val takeUn = kotlin.math.min(unBase, delta)
                                map["Uncommon"] = unBase - takeUn; delta -= takeUn
                            }
                            if (delta > 1e-9) {
                                val curTargetSum = targets.sumOf { map[it] ?: 0.0 }
                                val scale = if (curTargetSum <= 0.0) 0.0 else (curTargetSum - delta) / curTargetSum
                                targets.forEach { t -> map[t]?.let { map[t] = it * scale } }
                            }
                        } else {
                            val nonTargetBase = baseSum - targetBase
                            val nonTargetNew = baseSum - targetNew
                            val nonScale = if (nonTargetBase <= 0.0) 0.0 else nonTargetNew / nonTargetBase
                            map = base.associate { (k, v) -> k to if (k in targets) v * mult else v * nonScale }.toMutableMap()
                        }
                    }
                    if (line == UpgradeLine.WISE && rarityRodActive()) {
                        // Apply Rarity Rod finalize: Common -> 0, split to Uncommon/Epic; triple Legendary/Mythic; keep Rare static
                        val order = base.map { it.first }
                        val common = map["Common"] ?: 0.0
                        map["Common"] = 0.0
                        val un = map["Uncommon"] ?: 0.0
                        val ep = map["Epic"] ?: 0.0
                        val ue = un + ep
                        if (ue > 0) {
                            map["Uncommon"] = un + common * (un / ue)
                            map["Epic"] = ep + common * (ep / ue)
                        } else {
                            map["Uncommon"] = un + common
                        }
                        val rareFixed = map["Rare"] ?: 15.0
                        map["Legendary"] = (map["Legendary"] ?: 0.0) * 3.0
                        map["Mythic"] = (map["Mythic"] ?: 0.0) * 3.0
                        var sum = map.values.sum()
                        if (sum > 100.0 + 1e-9) {
                            var over = sum - 100.0
                            val uePool = listOf("Uncommon", "Epic")
                            var ueSum = uePool.sumOf { map[it] ?: 0.0 }
                            if (ueSum > 1e-9) {
                                val scale = (ueSum - over).coerceAtLeast(0.0) / ueSum
                                uePool.forEach { k -> map[k] = (map[k] ?: 0.0) * scale }
                                over -= (ueSum - (ueSum * scale))
                            }
                            if (over > 1e-6) {
                                val lmPool = listOf("Legendary", "Mythic")
                                val lmSum = lmPool.sumOf { map[it] ?: 0.0 }
                                if (lmSum > 1e-9) {
                                    val lms = (lmSum - over).coerceAtLeast(0.0) / lmSum
                                    lmPool.forEach { k -> map[k] = (map[k] ?: 0.0) * lms }
                                }
                            }
                            map["Rare"] = rareFixed
                        } else if (sum < 100.0 - 1e-9) {
                            map["Uncommon"] = (map["Uncommon"] ?: 0.0) + (100.0 - sum)
                        }
                        return order.map { it to (map[it] ?: 0.0) }
                    }
                    return base.map { it.first to (map[it.first] ?: 0.0) }
                }
                fun renderBreakdown(title: String, dist: List<Pair<String, Double>>, pType: Double) {
                    StringWidget(Component.literal(title).mccFont().withStyle(ChatFormatting.GRAY), font)
                        .at(row++, 0, settings = LayoutConstants.LEFT)
                    val sb = StringBuilder()
                    dist.forEachIndexed { idx, (k, v) ->
                        if (idx > 0) sb.append(" | ")
                        val exp = catchesLeft * (pType / 100.0) * (v / 100.0)
                        sb.append("$k ${"""%.2f""".format(exp)}")
                    }
                    StringWidget(Component.literal(sb.toString()).mccFont(), font).at(row++, 0, settings = LayoutConstants.LEFT)
                }

                val wiseDist = applyHookMultiplier(UpgradeLine.WISE, hookBase(UpgradeLine.WISE), hookTargets(UpgradeLine.WISE), computeHookMult(UpgradeLine.WISE))
                val strongDist = applyHookMultiplier(UpgradeLine.STRONG, hookBase(UpgradeLine.STRONG), hookTargets(UpgradeLine.STRONG), computeHookMult(UpgradeLine.STRONG))
                val glimDist = applyHookMultiplier(UpgradeLine.GLIMMERING, hookBase(UpgradeLine.GLIMMERING), hookTargets(UpgradeLine.GLIMMERING), computeHookMult(UpgradeLine.GLIMMERING))
                val greedyDist = applyHookMultiplier(UpgradeLine.GREEDY, hookBase(UpgradeLine.GREEDY), hookTargets(UpgradeLine.GREEDY), computeHookMult(UpgradeLine.GREEDY))
                val luckyDist = applyHookMultiplier(UpgradeLine.LUCKY, hookBase(UpgradeLine.LUCKY), hookTargets(UpgradeLine.LUCKY), computeHookMult(UpgradeLine.LUCKY))

                // Combined rarity x size for fish
                fun asMap(dist: List<Pair<String, Double>>): Map<String, Double> = dist.associate { it.first to it.second }
                val wiseMap = asMap(wiseDist)
                val strongMap = asMap(strongDist)
                val sizes = listOf("Average", "Large", "Massive", "Gargantuan")
                val rarities = listOf("Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic")

                // Proper table: Fish rarity (rows) x size (columns), letters colored
                fun rarityColor(r: String): Int = when (r) {
                    "Common" -> cc.pe3epwithyou.trident.state.Rarity.COMMON.color
                    "Uncommon" -> cc.pe3epwithyou.trident.state.Rarity.UNCOMMON.color
                    "Rare" -> cc.pe3epwithyou.trident.state.Rarity.RARE.color
                    "Epic" -> cc.pe3epwithyou.trident.state.Rarity.EPIC.color
                    "Legendary" -> cc.pe3epwithyou.trident.state.Rarity.LEGENDARY.color
                    "Mythic" -> cc.pe3epwithyou.trident.state.Rarity.MYTHIC.color
                    else -> ChatFormatting.WHITE.color!!
                }
                fun sizeColor(s: String): Int = when (s) {
                    "Average" -> FishWeightColor.AVERAGE.color
                    "Large" -> FishWeightColor.LARGE.color
                    "Massive" -> FishWeightColor.MASSIVE.color
                    "Gargantuan" -> FishWeightColor.GARGANTUAN.color
                    else -> ChatFormatting.WHITE.color!!
                }

                // Render header as a single line: A L M G (colored)
                if (showFishRow) run {
                    var headerComp = Component.literal("     ").mccFont()
                    sizes.forEach { s ->
                        val letter = s.first().uppercase()
                        headerComp = headerComp.append(Component.literal("$letter     ").mccFont().withColor(sizeColor(s)))
                    }
                    StringWidget(headerComp, font).at(row++, 0, settings = LayoutConstants.LEFT).alignLeft()
                }
                // Render each rarity row as single line with colored rarity letter and values
                if (showFishRow) {
                    rarities.forEach { r ->
                        val wPct = (wiseMap[r] ?: 0.0) / 100.0
                        var lineComp = Component.literal(r.first().uppercase()).mccFont().withColor(rarityColor(r))
                        sizes.forEach { s ->
                            val sPct = (strongMap[s] ?: 0.0) / 100.0
                            val exp = catchesLeft * (pFish/100.0) * wPct * sPct
                            lineComp = lineComp.append(Component.literal("  ${"""%.2f""".format(exp)}").mccFont().withStyle(ChatFormatting.AQUA))
                        }
                        StringWidget(lineComp, font).at(row++, 0, settings = LayoutConstants.LEFT).alignLeft()
                    }
                }

                // Other categories (non-fish) as single-row tables with colored letters
                fun renderOneDimTable(title: String, labels: List<String>, dist: List<Pair<String, Double>>, pType: Double, colorFor: (String) -> Int) {
                    StringWidget(Component.literal(title).mccFont().withStyle(ChatFormatting.GRAY), font)
                        .at(row++, 0, settings = LayoutConstants.LEFT)
                    var headerComp = Component.literal(" ").mccFont()
                    labels.forEachIndexed { idx, l ->
                        val letter = l.first().uppercase()
                        headerComp = headerComp.append(
                            Component.literal(if (idx == 0) letter else "     $letter").mccFont().withColor(colorFor(l))
                        )
                    }
                    StringWidget(headerComp, font).at(row++, 0, settings = LayoutConstants.LEFT).alignLeft()
                    val map = dist.associate { it.first to it.second }
                    var valuesComp = Component.literal("").mccFont()
                    labels.forEach { l ->
                        val pct = (map[l] ?: 0.0) / 100.0
                        val exp = catchesLeft * (pType/100.0) * pct
                        valuesComp = valuesComp.append(Component.literal(if (valuesComp.string.isEmpty()) "${"""%.2f""".format(exp)}" else "  ${"""%.2f""".format(exp)}").mccFont().withStyle(ChatFormatting.AQUA))
                    }
                    StringWidget(valuesComp, font).at(row++, 0, settings = LayoutConstants.LEFT).alignLeft()
                }
                if (showPearlRow) renderOneDimTable("Pearls (quality)", listOf("Rough","Polished","Pristine"), glimDist, pPearl) { s ->
                    when (s) {
                        "Rough" -> PearlQualityColor.ROUGH.color
                        "Polished" -> PearlQualityColor.POLISHED.color
                        "Pristine" -> PearlQualityColor.PRISTINE.color
                        else -> ChatFormatting.WHITE.color!!
                    }
                }
                if (showTreasureRow) renderOneDimTable("Treasure (rarity)", rarities, greedyDist, pTreasure) { r -> rarityColor(r) }
                if (showSpiritRow) renderOneDimTable("Spirits (purity)", listOf("Normal","Refined","Pure"), luckyDist, pSpirit) { s ->
                    when (s) {
                        "Normal" -> SpiritPurityColor.NORMAL.color
                        "Refined" -> SpiritPurityColor.REFINED.color
                        "Pure" -> SpiritPurityColor.PURE.color
                        else -> ChatFormatting.WHITE.color!!
                    }
                }

                // Token return calculation (fish only)
                fun tokenValue(r: String, s: String): Int = when (r) {
                    "Common" -> when (s) {
                        "Average" -> 40; "Large" -> 200; "Massive" -> 400; "Gargantuan" -> 2000; else -> 0
                    }
                    "Uncommon" -> when (s) {
                        "Average" -> 80; "Large" -> 400; "Massive" -> 800; "Gargantuan" -> 4000; else -> 0
                    }
                    "Rare" -> when (s) {
                        "Average" -> 160; "Large" -> 800; "Massive" -> 1600; "Gargantuan" -> 8000; else -> 0
                    }
                    "Epic" -> when (s) {
                        "Average" -> 400; "Large" -> 2000; "Massive" -> 4000; "Gargantuan" -> 20000; else -> 0
                    }
                    "Legendary" -> when (s) {
                        "Average" -> 800; "Large" -> 4000; "Massive" -> 8000; "Gargantuan" -> 40000; else -> 0
                    }
                    "Mythic" -> when (s) {
                        "Average" -> 2000; "Large" -> 10000; "Massive" -> 20000; "Gargantuan" -> 100000; else -> 0
                    }
                    else -> 0
                }
                if (pFish > 0.0) {
                    val baseTokensPerFish: Double = rarities.sumOf { r ->
                        sizes.sumOf { s -> tokenValue(r, s) * ((wiseMap[r] ?: 0.0) / 100.0) * ((strongMap[s] ?: 0.0) / 100.0) }
                    }
                    val elusiveFactor = 1.0 + 2.0 * (pElusiveEff / 100.0)
                    val expFishCount = catchesLeft * (pFish / 100.0)
                    val tokensFromFish = expFishCount * baseTokensPerFish * elusiveFactor
                    // Magnet fish expected tokens (approximate, no elusive multiplier)
                    val tokensFromMagFish = magFish * baseTokensPerFish
                    val totalTokens = tokensFromFish + tokensFromMagFish
                    val tokensPerCatch = if (catchesLeft > 0.0) totalTokens / catchesLeft else 0.0
                    val tokenComp = Component.literal("Expected tokens (fish): ")
                        .mccFont()
                        .append(Component.literal("~${"""%.0f""".format(totalTokens)}").mccFont().withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("  (~${"""%.0f""".format(tokensPerCatch)}/catch)").mccFont().withStyle(ChatFormatting.GRAY))
                    StringWidget(tokenComp, font).at(row++, 0, settings = LayoutConstants.LEFT)
                }
            }
        }

        if (spot.hookPercents.isNotEmpty()) {
            StringWidget(Component.literal("Hooks").mccFont().withStyle(ChatFormatting.GRAY), font).at(row++, 0, settings = LayoutConstants.LEFT)
            UpgradeLine.entries.forEach { l ->
                val v = spot.hookPercents[l] ?: return@forEach
                val baseColor = when(l){
                    UpgradeLine.STRONG -> FishWeightColor.baseColor
                    UpgradeLine.WISE -> FishRarityColor.baseColor
                    UpgradeLine.GLIMMERING -> PearlQualityColor.baseColor
                    UpgradeLine.GREEDY -> TreasureRarityColor.baseColor
                    UpgradeLine.LUCKY -> SpiritPurityColor.baseColor
                }
                val t = Component.literal("${lineLabel(l)} Hook: ").mccFont()
                    .append(Component.literal("+${"""%.2f""".format(v)}% ").mccFont().withColor(baseColor))
                StringWidget(t, font).at(row++, 0, settings = LayoutConstants.LEFT)
            }
        }

        if (spot.magnetPercents.isNotEmpty()) {
            StringWidget(Component.literal("Magnets").mccFont().withStyle(ChatFormatting.GRAY), font).at(row++, 0, settings = LayoutConstants.LEFT)
            UpgradeLine.entries.forEach { l ->
                val v = spot.magnetPercents[l] ?: return@forEach
                val label = when (l) {
                    UpgradeLine.STRONG -> "XP Magnet"
                    UpgradeLine.WISE -> "Fish Magnet"
                    UpgradeLine.GLIMMERING -> "Pearl Magnet"
                    UpgradeLine.GREEDY -> "Treasure Magnet"
                    UpgradeLine.LUCKY -> "Spirit Magnet"
                }
                val baseColor = when(l){
                    UpgradeLine.STRONG -> FishWeightColor.baseColor
                    UpgradeLine.WISE -> FishRarityColor.baseColor
                    UpgradeLine.GLIMMERING -> PearlQualityColor.baseColor
                    UpgradeLine.GREEDY -> TreasureRarityColor.baseColor
                    UpgradeLine.LUCKY -> SpiritPurityColor.baseColor
                }
                val t = Component.literal("$label: ").mccFont()
                    .append(Component.literal("+${"""%.2f""".format(v)}% ").mccFont().withColor(baseColor))
                StringWidget(t, font).at(row++, 0, settings = LayoutConstants.LEFT)
            }
        }

        // Chance perks: show only if modified
        val chanceRows = listOf(
            "Fish Chance" to spot.fishChanceBonusPercent,
            "Elusive Chance" to spot.elusiveChanceBonusPercent,
            "Pearl Chance" to spot.pearlChanceBonusPercent,
            "Treasure Chance" to spot.treasureChanceBonusPercent,
            "Spirit Chance" to spot.spiritChanceBonusPercent,
        ).filter { it.second != 0.0 }
        val showWayfinder = spot.wayfinderDataBonus != 0.0
        if (chanceRows.isNotEmpty() || showWayfinder) {
            StringWidget(Component.literal("Chances").mccFont().withStyle(ChatFormatting.GRAY), font).at(row++, 0, settings = LayoutConstants.LEFT)
            chanceRows.forEach { (label, value) ->
                StringWidget(
                    Component.literal("$label: ").mccFont()
                        .append(Component.literal("+${"""%.2f""".format(value)}% ").mccFont().withStyle(ChatFormatting.AQUA)),
                    font
                ).at(row++, 0, settings = LayoutConstants.LEFT)
            }
            if (showWayfinder) {
                StringWidget(
                    Component.literal("Wayfinder Data: ").mccFont()
                        .append(Component.literal("+${"""%.2f""".format(spot.wayfinderDataBonus)}").mccFont().withStyle(ChatFormatting.AQUA)),
                    font
                ).at(row++, 0, settings = LayoutConstants.LEFT)
            }
        }

    }

    override fun refresh() {
        title = getWidgetTitle()
        super.refresh()
    }
}


