package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PortFireboxScreen extends AbstractContainerScreen<PortFireboxMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/machine/gui_firebox.png");
    public PortFireboxScreen(PortFireboxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int heat = Math.max(0, Math.min(69, menu.heat() * 69 / 100_000));
        graphics.blit(TEXTURE, leftPos + 81, topPos + 28, 176, 0, heat, 5);
        int burn = Math.max(0, Math.min(70, menu.burnTime() * 70 / Math.max(1, menu.maxBurnTime())));
        graphics.blit(TEXTURE, leftPos + 81, topPos + 37, 176, 5, burn, 5);
        if (menu.burnTime() > 0) graphics.blit(TEXTURE, leftPos + 25, topPos + 26, 176, 10, 18, 18);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 80 && mouseX < leftPos + 151
                && mouseY >= topPos + 27 && mouseY < topPos + 34) {
            graphics.renderTooltip(font, Component.literal(menu.heat() + " / 100000 TU"), mouseX, mouseY);
        }
    }
}
