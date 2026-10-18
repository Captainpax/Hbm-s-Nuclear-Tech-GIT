package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Source GUI texture with live HE and smelting progress overlays. */
public final class PortElectricFurnaceScreen extends AbstractContainerScreen<PortElectricFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/processing/gui_electric_furnace.png");

    public PortElectricFurnaceScreen(PortElectricFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int energy = Math.max(0, Math.min(34, menu.power() * 34 / (int) PortElectricFurnaceBlockEntity.CAPACITY));
        if (energy > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 52 - energy,
                176, 64 - energy, 16, energy);
        int progress = Math.max(0, Math.min(28, menu.progress() * 28 / PortElectricFurnaceBlockEntity.DURATION));
        if (progress > 0) graphics.blit(TEXTURE, leftPos + 43, topPos + 36, 176, 0, progress, 12);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 152 && mouseX < leftPos + 168
                && mouseY >= topPos + 18 && mouseY < topPos + 52) {
            graphics.renderTooltip(font, Component.literal(menu.power() + " / "
                    + PortElectricFurnaceBlockEntity.CAPACITY + " HE"), mouseX, mouseY);
        }
    }
}
