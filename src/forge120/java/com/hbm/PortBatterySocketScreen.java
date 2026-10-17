package com.hbm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/** Source 176x181 battery-socket texture with modern synced controls. */
public final class PortBatterySocketScreen extends AbstractContainerScreen<PortBatterySocketMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HbmPort.MOD_ID,
            "textures/gui/storage/gui_battery_socket.png");
    private static final String[] MODES = {"input", "buffer", "output", "disabled"};
    private static final String[] PRIORITIES = {"low", "low", "normal", "high", "high"};

    public PortBatterySocketScreen(PortBatterySocketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 181;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (inside(mouseX, mouseY, 62, 17, 34, 52)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.hbm.battery_charge",
                    menu.power(), menu.capacity()), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 106, 16, 18, 18)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.hbm.battery_low_mode",
                    modeName(menu.lowMode())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 106, 52, 18, 18)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.hbm.battery_high_mode",
                    modeName(menu.highMode())), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 125, 35, 16, 16)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.hbm.battery_priority",
                    Component.translatable("battery.priority." + PRIORITIES[Math.max(0,
                            Math.min(PRIORITIES.length - 1, menu.priority()))])), mouseX, mouseY);
        }
    }

    private Component modeName(int index) {
        return Component.translatable("battery.mode." + MODES[Math.max(0, Math.min(3, index))]);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.capacity() > 0 && menu.power() > 0) {
            int filled = Math.max(0, Math.min(52, (int) ((double) menu.power() / menu.capacity() * 52)));
            graphics.blit(TEXTURE, leftPos + 62, topPos + 69 - filled, 176, 52 - filled, 34, filled);
        }
        graphics.blit(TEXTURE, leftPos + 106, topPos + 16, 176, 52 + clampMode(menu.lowMode()) * 18, 18, 18);
        graphics.blit(TEXTURE, leftPos + 106, topPos + 52, 176, 52 + clampMode(menu.highMode()) * 18, 18, 18);
        int priority = Math.max(1, Math.min(3, menu.priority()));
        graphics.blit(TEXTURE, leftPos + 125, topPos + 35, 194, 52 + (priority - 1) * 16, 16, 16);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && Minecraft.getInstance().gameMode != null) {
            int control = inside(mouseX, mouseY, 106, 16, 18, 18) ? 0
                    : inside(mouseX, mouseY, 106, 52, 18, 18) ? 1
                    : inside(mouseX, mouseY, 125, 35, 16, 16) ? 2 : -1;
            if (control >= 0) {
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, control);
                Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    private static int clampMode(int mode) {
        return Math.max(0, Math.min(3, mode));
    }
}
