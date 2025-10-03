package cc.pe3epwithyou.trident.interfaces.meter

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.interfaces.shared.TridentDialog
import cc.pe3epwithyou.trident.interfaces.themes.TridentThemed
import cc.pe3epwithyou.trident.state.FontCollection
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.mccFont
import com.noxcrew.sheeplib.LayoutConstants
import com.noxcrew.sheeplib.layout.grid
import com.noxcrew.sheeplib.theme.Themed
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.withFont

class MeterDialog(x: Int, y: Int, key: String) : TridentDialog(x, y, key), Themed by TridentThemed {
    override fun layout(): GridLayout = grid {
        val font = Minecraft.getInstance().font
        var row = 0

        // Progress bar (styled like QuestWidget)
        val COMP_BLANK = FontCollection.get("_fonts/icon/progress_counter/empty.png", 7, 7)
        val COMP_HALF = FontCollection.get("_fonts/icon/progress_counter/half.png", 7, 7)
        val COMP_FULL = FontCollection.get("_fonts/icon/progress_counter/full.png", 7, 7)

        fun progressBarComponent(progress: Float, width: Int, groups: Int = 0): Component {
            if (width <= 0) return Component.empty()
            val subPerChar = 2 // empty/half/full
            val clamped = progress.coerceIn(0f, 1f)
            val totalSubUnits = width * subPerChar
            val filledSubUnits = (clamped * totalSubUnits).toInt()
            val groupSize = if (groups > 0) kotlin.math.max(1, width / groups) else Int.MAX_VALUE
            var component = Component.empty()
            for (i in 0 until width) {
                val startUnit = i * subPerChar
                val remain = kotlin.math.max(0, kotlin.math.min(subPerChar, filledSubUnits - startUnit))
                val piece = when {
                    remain >= subPerChar -> COMP_FULL
                    remain * 2 >= subPerChar -> COMP_HALF
                    else -> COMP_BLANK
                }
                component = component.append(piece)
                if (groups > 0 && (i + 1) % groupSize == 0 && i != width - 1) {
                    component = component.append(Component.literal("\uE001").withFont(ResourceLocation.withDefaultNamespace("padding")))
                }
            }
            return component
        }

        val daily = TridentClient.playerState.dailyMeter
        val weekly = TridentClient.playerState.weeklyMeter
        // Use the mean of short and long EMA as the headline XP/hour to avoid session drift
        val emaS = daily.emaShortXpPerHour
        val emaL = daily.emaLongXpPerHour
        val xpPerHour = if (emaS.isFinite() && emaL.isFinite()) (emaS + emaL) / 2.0 else (if (emaS.isFinite()) emaS else emaL)

        StringWidget(Component.literal("DAILY METER").mccFont().withStyle(ChatFormatting.GRAY), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)
        run {
            val frac = if (daily.progressTarget > 0) daily.progressCurrent.toFloat() / daily.progressTarget.toFloat() else 0f
            val bar = progressBarComponent(frac, 25, 5)
            val label = Component.literal("  ${daily.progressCurrent}/${daily.progressTarget} XP").mccFont().withStyle(ChatFormatting.AQUA)
            val combined = Component.empty().append(bar).append(label)
            StringWidget(combined, font).at(row++, 0, settings = LayoutConstants.LEFT)
        }
        StringWidget(Component.literal("  Claims: ${daily.claimsCurrent}/${daily.claimsMax}").mccFont().withStyle(ChatFormatting.AQUA), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)
        run {
            val xpText = Component.literal("XP/hour: ${String.format("%.0f", xpPerHour)} ").mccFont().withStyle(ChatFormatting.AQUA)
            val xpW = StringWidget(xpText, font)
            xpW.alignLeft()
            xpW.at(row, 0, settings = LayoutConstants.LEFT)

            val resetLabel = Component.literal("Reset").mccFont()
            val btn = Button.builder(resetLabel) {
                val now = System.currentTimeMillis()
                val ps = cc.pe3epwithyou.trident.client.TridentClient.playerState
                // Daily
                ps.dailyMeter.sessionStartMs = now
                ps.dailyMeter.sessionXpGained = 0
                ps.dailyMeter.xpEvents.clear()
                ps.dailyMeter.emaShortXpPerHour = 0.0
                ps.dailyMeter.emaLongXpPerHour = 0.0
                // Weekly
                ps.weeklyMeter.sessionStartMs = now
                ps.weeklyMeter.sessionXpGained = 0
                ps.weeklyMeter.xpEvents.clear()
                ps.weeklyMeter.emaShortXpPerHour = 0.0
                ps.weeklyMeter.emaLongXpPerHour = 0.0
                this@MeterDialog.refresh()
            }.bounds(0, 0, 40, 12).build()
            btn.at(row++, 0, settings = LayoutConstants.RIGHT)
        }

        row++
        StringWidget(Component.literal("WEEKLY VAULT").mccFont().withStyle(ChatFormatting.GRAY), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)
        run {
            val frac = if (weekly.progressTarget > 0) weekly.progressCurrent.toFloat() / weekly.progressTarget.toFloat() else 0f
            val bar = progressBarComponent(frac, 25, 5)
            val label = Component.literal("  ${weekly.progressCurrent}/${weekly.progressTarget} XP").mccFont().withStyle(ChatFormatting.AQUA)
            val combined = Component.empty().append(bar).append(label)
            StringWidget(combined, font).at(row++, 0, settings = LayoutConstants.LEFT)
        }
        StringWidget(Component.literal("  Stored: ${weekly.claimsCurrent}/${weekly.claimsMax}").mccFont().withStyle(ChatFormatting.AQUA), font)
            .at(row++, 0, settings = LayoutConstants.LEFT)
    }
}


