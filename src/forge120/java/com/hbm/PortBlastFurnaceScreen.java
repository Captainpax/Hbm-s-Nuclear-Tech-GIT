package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Source blast-furnace background with fuel and steel-operation indicators. */
public final class PortBlastFurnaceScreen extends AbstractContainerScreen<PortBlastFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/processing/gui_blast_furnace.png");

    public PortBlastFurnaceScreen(PortBlastFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int fuel = Math.max(0, Math.min(26, menu.fuel() * 26 / PortBlastFurnaceBlockEntity.MAX_FUEL));
        int progress = Math.max(0, Math.min(88 - fuel,
                menu.progress() * (88 - fuel) / (PortBlastFurnaceRecipes.STEEL_DURATION * 2)));
        if (progress > 0) graphics.blit(TEXTURE, leftPos + 62, topPos + 106 - progress - fuel,
                176, 102 - progress - fuel, 56, progress);
        if (fuel > 0) graphics.blit(TEXTURE, leftPos + 62, topPos + 106 - fuel,
                176, 128 - fuel, 56, fuel);
        if (menu.progressing()) graphics.blit(TEXTURE, leftPos + 81, topPos + 64, 176, 0, 14, 14);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 24 && mouseX < leftPos + 44
                && mouseY >= topPos + 16 && mouseY < topPos + 36) {
            graphics.renderTooltip(font, Component.literal(menu.flueGas() + "/1000 mB flue gas"), mouseX, mouseY);
        }
    }
}
