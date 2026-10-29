package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PortCrucibleScreen extends AbstractContainerScreen<PortCrucibleMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/processing/gui_crucible.png");
    public PortCrucibleScreen(PortCrucibleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 214;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int heat = Math.max(0, Math.min(68, menu.heat() * 68 / PortCrucibleBlockEntity.MAX_HEAT));
        graphics.fill(leftPos + 22, topPos + 94 - heat, leftPos + 27, topPos + 94, 0xffce4d12);
        int copper = Math.max(0, Math.min(30, menu.moltenCopper() * 30 / PortFoundryMoldBlockEntity.CAST_PLATE_QUANTA));
        graphics.fill(leftPos + 54, topPos + 94 - copper, leftPos + 85, topPos + 94, 0xffd5823a);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0xffffff);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
