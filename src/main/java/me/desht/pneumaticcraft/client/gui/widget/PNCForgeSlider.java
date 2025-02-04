package me.desht.pneumaticcraft.client.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.GuiUtils;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.util.function.Consumer;

public class PNCForgeSlider extends ForgeSlider {
    private final Consumer<PNCForgeSlider> onApply;

    public PNCForgeSlider(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, double stepSize, int precision, boolean drawString, Consumer<PNCForgeSlider> onApply) {
        super(x, y, width, height, prefix, suffix, minValue, maxValue, currentValue, stepSize, precision, drawString);
        this.onApply = onApply;
    }

    public PNCForgeSlider(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, boolean drawString, Consumer<PNCForgeSlider> onApply) {
        super(x, y, width, height, prefix, suffix, minValue, maxValue, currentValue, drawString);
        this.onApply = onApply;
    }

    @Override
    protected void renderBg(PoseStack pPoseStack, Minecraft pMinecraft, int pMouseX, int pMouseY) {
        // this works for slider heights of other than 20
        int xPos = this.x + (int)(this.value * (double)(this.width - 8));
        int vOff = (this.isHoveredOrFocused() ? 2 : 1) * 20;
        GuiUtils.drawContinuousTexturedBox(pPoseStack, WIDGETS_LOCATION, xPos, this.y, 0, 46 + vOff, 8, this.height, 200, 20, 2, 3, 2, 2, this.getBlitOffset());
    }

    @Override
    protected void applyValue() {
        if (onApply != null) onApply.accept(this);
    }
}
