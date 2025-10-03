package cc.pe3epwithyou.trident.interfaces.fishing.widgets

import cc.pe3epwithyou.trident.interfaces.shared.widgets.ItemWidget
import cc.pe3epwithyou.trident.utils.Model
import com.noxcrew.sheeplib.CompoundWidget
import com.noxcrew.sheeplib.layout.CanvasLayout
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import cc.pe3epwithyou.trident.utils.extensions.ComponentExtensions.mccFont

class IconWithLabelWidget(
    private val model: Model,
    private val label: Component?,
    private val marginRight: Int = 0,
    private val overlayLetter: String? = null,
    private val overlayTooltip: Component? = null,
    private val overlayLetterSecondary: String? = null,
    private val overlayTooltipSecondary: Component? = null
) : CompoundWidget(0, 0, model.width + marginRight, 0) {

    private val overlayExtraWidth: Int = run {
        val font = Minecraft.getInstance().font
        val primaryW = overlayLetter?.let { Component.literal(it).mccFont() }?.let { font.width(it.visualOrderText) } ?: 0
        val secondaryW = overlayLetterSecondary?.let { Component.literal(it).mccFont() }?.let { font.width(it.visualOrderText) } ?: 0
        val maxW = kotlin.math.max(primaryW, secondaryW)
        if (maxW == 0) 0 else maxW + 2
    }

    override val layout: CanvasLayout = CanvasLayout(
        model.width + marginRight + overlayExtraWidth,
        model.height + 9,
    ).apply {
        ItemWidget(model).at(top = 0, left = 0)
        // Optional small badge next to the icon (safe fallback, no transforms)
        overlayLetter?.let { letter ->
            val font = Minecraft.getInstance().font
            val comp = Component.literal(letter).mccFont(offset = 0)
            val left = model.width + 1
            val topOffset = -3 // keep as high as possible
            val w = StringWidget(comp, font)
            if (overlayTooltip != null) {
                w.setTooltip(Tooltip.create(overlayTooltip))
            }
            w.at(top = topOffset, left = left)
        }
        overlayLetterSecondary?.let { letter ->
            val font = Minecraft.getInstance().font
            val comp = Component.literal(letter).mccFont(offset = 3)
            val left = model.width + 1
            val lineH = font.lineHeight
            val extraSpacing = 4 // add more vertical spacing below the R
            val below = (lineH + extraSpacing).coerceAtMost(model.height - lineH).coerceAtLeast(0)
            val w = StringWidget(comp, font)
            if (overlayTooltipSecondary != null) {
                w.setTooltip(Tooltip.create(overlayTooltipSecondary))
            }
            w.at(top = below, left = left)
        }
        label?.let {
            val font = Minecraft.getInstance().font
            var chosen = it.copy().mccFont(offset = 0)
            var widthPx = font.width(chosen.visualOrderText)
            val offsets = listOf(3, 2, 1, 0)
            for (off in offsets) {
                val candidate = it.copy().mccFont(offset = off)
                val w = font.width(candidate.visualOrderText)
                if (w <= model.width) {
                    chosen = candidate
                    widthPx = w
                    break
                }
                chosen = candidate
                widthPx = w
            }
            val left = (model.width - widthPx) / 2
            StringWidget(chosen, font).at(top = model.height, left = left)
        }
    }

    override fun getWidth(): Int = model.width + marginRight + overlayExtraWidth
    override fun getHeight(): Int = layout.height

    init {
        layout.arrangeElements()
        layout.visitWidgets(this::addChild)
    }

}


