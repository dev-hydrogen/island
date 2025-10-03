package cc.pe3epwithyou.trident.interfaces.fishing

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.interfaces.shared.TridentDialog
import cc.pe3epwithyou.trident.interfaces.themes.DialogTitle
import cc.pe3epwithyou.trident.interfaces.themes.TridentThemed
import cc.pe3epwithyou.trident.state.FishRarityColor
import cc.pe3epwithyou.trident.state.FishWeightColor
import cc.pe3epwithyou.trident.state.Rarity
import cc.pe3epwithyou.trident.state.fishing.UpgradeLine
import cc.pe3epwithyou.trident.state.fishing.UpgradeType
import cc.pe3epwithyou.trident.state.fishing.PerkStateCalculator
import cc.pe3epwithyou.trident.utils.Resources
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.mccFont
import cc.pe3epwithyou.trident.utils.Model
import com.noxcrew.sheeplib.LayoutConstants
import com.noxcrew.sheeplib.dialog.title.DialogTitleWidget
import com.noxcrew.sheeplib.layout.CanvasLayout
import com.noxcrew.sheeplib.layout.grid
import com.noxcrew.sheeplib.theme.Themed
import com.noxcrew.sheeplib.util.opacity
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

class FishDialog(x: Int, y: Int, key: String) : TridentDialog(x, y, key), Themed by TridentThemed {
    private companion object {
        private val TITLE_COLOR: Int = 0x54fcfc opacity 127
    }

    private fun getWidgetTitle(): DialogTitleWidget {
        val text = Component.literal(" FISH").mccFont()
        return DialogTitle(this, text, TITLE_COLOR)
    }

    override var title = getWidgetTitle()

    private fun sanitizePath(s: String): String = s.lowercase()
        .replace("'", "")
        .replace(" ", "_")
        .replace(Regex("[^a-z0-9_]+"), "")
        .replace(Regex("_+"), "_")

    private fun weightLetter(w: String): String = when (w.uppercase()) {
        "AVERAGE" -> "A"
        "LARGE" -> "L"
        "MASSIVE" -> "M"
        "GARGANTUAN" -> "G"
        "COLOSSAL" -> "C"
        else -> "?"
    }

    private fun weightColor(w: String): Int = when (w.uppercase()) {
        "AVERAGE" -> FishWeightColor.AVERAGE.color
        "LARGE" -> FishWeightColor.LARGE.color
        "MASSIVE" -> FishWeightColor.MASSIVE.color
        "GARGANTUAN" -> FishWeightColor.GARGANTUAN.color
        "COLOSSAL" -> FishWeightColor.MASSIVE.color
        else -> ChatFormatting.WHITE.color!!
    }

    private fun rarityColor(r: Rarity): Int = when (r) {
        Rarity.COMMON -> Rarity.COMMON.color
        Rarity.UNCOMMON -> Rarity.UNCOMMON.color
        Rarity.RARE -> Rarity.RARE.color
        Rarity.EPIC -> Rarity.EPIC.color
        Rarity.LEGENDARY -> Rarity.LEGENDARY.color
        Rarity.MYTHIC -> Rarity.MYTHIC.color
    }

    override fun layout(): GridLayout = grid {
        val font = Minecraft.getInstance().font
        // Ensure perk state is fresh
        TridentClient.playerState.perkState = PerkStateCalculator.recompute(TridentClient.playerState)

        val ps = TridentClient.playerState
        val island = ps.currentCollection

        StringWidget(
            Component.literal("ISLAND: ${island ?: "Unknown"}").mccFont().withStyle(ChatFormatting.GRAY),
            font
        ).at(0, 0, settings = LayoutConstants.LEFT)

        val records = if (island == null) emptyList() else ps.fishCollection.records
            .filter { it.collection.equals(island, true) }
            .filter { !it.fishName.contains("crab", true) }

        // Compute per-catch distributions needed
        val spot = ps.spot
        val tides = ps.tideLines
        // Rarity (wise) multiplier
        fun hookMult(line: UpgradeLine): Double {
            val basePts = ps.perkState.totals[line]?.get(UpgradeType.HOOK)?.total ?: 0
            val spotPct = if (spot.hasSpot) (spot.hookPercents[line] ?: 0.0) else 0.0
            val tidePct = if (tides.contains(line)) 20.0 else 0.0
            val effectivePts = basePts * (1.0 + (spotPct + tidePct) / 100.0)
            return 1.0 + (effectivePts * 0.1)
        }
        // Base distributions from existing dialogs
        val wiseBase = listOf(
            "Common" to 54.8, "Uncommon" to 25.0, "Rare" to 15.0, "Epic" to 4.0, "Legendary" to 1.0, "Mythic" to 0.2
        )
        val strongBase = listOf(
            "Average" to 90.3, "Large" to 7.5, "Massive" to 2.0, "Gargantuan" to 0.2
        )
        fun applyTargets(base: List<Pair<String, Double>>, targets: Set<String>, mult: Double): Map<String, Double> {
            if (mult == 1.0) return base.toMap()
            val sum = base.sumOf { it.second }
            val targSum = base.filter { it.first in targets }.sumOf { it.second }
            if (targSum == 0.0) return base.toMap()
            val newTarg = targSum * mult
            if (newTarg >= sum - 1e-9) {
                return base.associate { (k, v) -> k to if (k in targets) (v / targSum) * sum else 0.0 }
            }
            val nonBase = sum - targSum
            val nonNew = sum - newTarg
            val nonScale = if (nonBase <= 0.0) 0.0 else nonNew / nonBase
            return base.associate { (k, v) -> k to if (k in targets) v * mult else v * nonScale }
        }
        val wiseTargets = setOf("Epic", "Legendary", "Mythic")
        val strongTargets = setOf("Large", "Massive", "Gargantuan")
        val wiseDist = applyTargets(wiseBase, wiseTargets, hookMult(UpgradeLine.WISE))
        val strongDist = applyTargets(strongBase, strongTargets, hookMult(UpgradeLine.STRONG))
        val wiseMap = wiseDist
        val strongMap = strongDist

        // Fish chance per catch
        val fishBase = 40.0
        val fishSpot100 = spot.hasSpot && spot.fishChanceBonusPercent >= 100.0
        val pFish = when {
            fishSpot100 -> 100.0
            else -> fishBase
        }

        // Compute New Fish % Chance (any new species-weight this catch)
        fun allowedNow(ct: String): Boolean = when (ct.uppercase()) {
            "ALWAYS" -> true
            "DAY" -> ps.isDayTime
            "NIGHT" -> !ps.isDayTime
            else -> true
        }
        val weightsAll = listOf("AVERAGE", "LARGE", "MASSIVE", "GARGANTUAN")
        val byRarityCounts = records.groupBy { it.rarity }.mapValues { (_, list) -> list.count { allowedNow(it.catchTime) } }
        var newFishPct = 0.0
        records.forEach { rec ->
            if (!allowedNow(rec.catchTime)) return@forEach
            val nInRarity = byRarityCounts[rec.rarity] ?: 0
            if (nInRarity <= 0) return@forEach
            weightsAll.forEach { w ->
                val already = rec.caughtWeights.any { it.equals(w, true) }
                if (!already) {
                    val wisePct = wiseMap[rec.rarity.name.lowercase().replaceFirstChar { it.uppercase() }] ?: 0.0
                    val strongPct = strongMap[w.lowercase().replaceFirstChar { it.uppercase() }] ?: 0.0
                    val contrib = (pFish * wisePct / nInRarity) * (strongPct / 100.0)
                    newFishPct += contrib
                }
            }
        }
        if (newFishPct > 100.0) newFishPct = 100.0
        StringWidget(
            Component.literal("NEW FISH: ${"""%.2f""".format(newFishPct)}% ").mccFont().withStyle(ChatFormatting.GOLD),
            font
        ).at(0, 2, settings = LayoutConstants.LEFT)

        // Render rows of max 7
        val perRow = 7
        var row = 1
        var col = 0
        fun flushRow() {
            if (col != 0) {
                row += 4
                col = 0
            }
        }
        records.forEach { rec ->
            if (col == perRow) flushRow()

            val allowedNow = when (rec.catchTime.uppercase()) {
                "ALWAYS" -> true
                "DAY" -> ps.isDayTime
                "NIGHT" -> !ps.isDayTime
                else -> true
            }

            val islandPath = sanitizePath(rec.collection)
            val fishPath = sanitizePath(rec.fishName)
            val textureIslandPath = if (islandPath == "tropical_overgrowth") "tropical_undergrowth" else islandPath
            val tex = Resources.mcc("island_items/infinibag/fish/${textureIslandPath}/${fishPath}")
            val model = Model(tex, 16, 16)

            val x = col
            val rColor = rarityColor(rec.rarity)
            StringWidget(Component.literal(rec.fishName).mccFont().withColor(rColor), font)
                .at(row, x * 2, settings = LayoutConstants.CENTRE)

            cc.pe3epwithyou.trident.interfaces.fishing.widgets.IconWithLabelWidget(
                model,
                null,
                marginRight = 2,
            ).at(row + 1, x * 2, settings = LayoutConstants.CENTRE)

            // weights rows (A/L on first line, M/G on second), showing caught vs not caught and percentages
            val weights = listOf("AVERAGE", "LARGE", "MASSIVE", "GARGANTUAN")
            val nInRarity = records.count { it.rarity == rec.rarity && when (it.catchTime.uppercase()) {
                "ALWAYS" -> true
                "DAY" -> ps.isDayTime
                "NIGHT" -> !ps.isDayTime
                else -> true
            } }
            fun weightComp(w: String): Component {
                val caught = rec.caughtWeights.any { it.equals(w, true) }
                val per = if (allowedNow && nInRarity > 0) (pFish * (wiseMap[rec.rarity.name.lowercase().replaceFirstChar { it.uppercase() }] ?: 0.0) / nInRarity) * ((strongMap[w.lowercase().replaceFirstChar { it.uppercase() }] ?: 0.0) / 100.0) else 0.0
                val letter = Component.literal(weightLetter(w)).mccFont().withColor(weightColor(w))
                val pct = Component.literal(" ${"""%.1f""".format(per)}%").mccFont().withStyle(ChatFormatting.AQUA)
                val base = Component.literal("").mccFont().append(letter).append(pct)
                return base.withStyle(if (caught) ChatFormatting.AQUA else ChatFormatting.GRAY)
            }
            if (!allowedNow) {
                StringWidget(Component.literal("Time-locked").mccFont().withStyle(ChatFormatting.DARK_GRAY), font)
                    .at(row + 2, x * 2, settings = LayoutConstants.CENTRE)
            } else {
                val sep = Component.literal(" | ").mccFont().withStyle(ChatFormatting.DARK_GRAY)
                val line1 = Component.literal("").mccFont()
                    .append(weightComp("AVERAGE")).append(sep).append(weightComp("LARGE"))
                val line2 = Component.literal("").mccFont()
                    .append(weightComp("MASSIVE")).append(sep).append(weightComp("GARGANTUAN"))
                // Pack lines closer and higher: draw both immediately under the icon
                StringWidget(line1, font).at(row + 2, x * 2, settings = LayoutConstants.CENTRE)
                StringWidget(line2, font).at(row + 3, x * 2, settings = LayoutConstants.RIGHT)
            }

            col++
        }
    }

    override fun refresh() {
        title = getWidgetTitle()
        super.refresh()
    }
}


