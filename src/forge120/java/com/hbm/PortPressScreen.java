package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Legacy press GUI texture and live burn/ram indicators. */
public final class PortPressScreen extends AbstractContainerScreen<PortPressMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/gui_press.png");

    public PortPressScreen(PortPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 202;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.burnTime() >= 20) graphics.blit(TEXTURE, leftPos + 27, topPos + 36, 0, 202, 14, 14);
        int ram = Math.max(0, Math.min(16, menu.press() * 16 / PortPressBlockEntity.MAX_PRESS));
        if (ram > 0) graphics.blit(TEXTURE, leftPos + 79, topPos + 35, 14, 202, 18, ram);
        int heat = Math.max(0, Math.min(36, menu.speed() * 36 / PortPressBlockEntity.MAX_SPEED));
        graphics.fill(leftPos + 16, topPos + 15, leftPos + 16 + heat, topPos + 17, 0xff7f0000);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 25 && mouseX < leftPos + 43
                && mouseY >= topPos + 16 && mouseY < topPos + 34) {
            graphics.renderTooltip(font, Component.literal(menu.speed() * 100 / PortPressBlockEntity.MAX_SPEED + "%"), mouseX, mouseY);
        } else if (mouseX >= leftPos + 25 && mouseX < leftPos + 43
                && mouseY >= topPos + 34 && mouseY < topPos + 52) {
            graphics.renderTooltip(font, Component.literal(menu.burnTime() / 200 + " operations left"), mouseX, mouseY);
        }
    }
}
