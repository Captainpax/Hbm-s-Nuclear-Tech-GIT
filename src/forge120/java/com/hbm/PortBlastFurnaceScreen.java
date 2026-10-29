package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.math.Axis;

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
        drawGauge(graphics, leftPos + 34, topPos + 80, menu.airblast() / 4_000.0F);
        drawGauge(graphics, leftPos + 34, topPos + 26, menu.flueGas() / 1_000.0F);
    }

    /** The source gauge's 270-degree triangular needle with a black outline. */
    private static void drawGauge(GuiGraphics graphics, int x, int y, float fill) {
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-Math.max(0, Math.min(1, fill)) * 270 - 45));
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 1.5F : 1.0F;
            int red = layer == 0 ? 0 : 128;
            buffer.vertex(pose.last().pose(), 0, 5 * scale, 0).color(red, 0, 0, 255).endVertex();
            buffer.vertex(pose.last().pose(), scale, -2 * scale, 0).color(red, 0, 0, 255).endVertex();
            buffer.vertex(pose.last().pose(), -scale, -2 * scale, 0).color(red, 0, 0, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        pose.popPose();
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
        if (mouseX >= leftPos + 24 && mouseX < leftPos + 44
                && mouseY >= topPos + 70 && mouseY < topPos + 90) {
            graphics.renderTooltip(font, Component.literal(menu.airblast() + "/4000 mB airblast"), mouseX, mouseY);
        }
    }
}
