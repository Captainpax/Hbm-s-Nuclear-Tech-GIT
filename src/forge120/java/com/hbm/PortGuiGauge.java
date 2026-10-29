package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.math.Axis;

/** Shared source 270-degree dial needle. */
final class PortGuiGauge {
    private PortGuiGauge() {}

    /** The source gauge's 270-degree triangular needle with a black outline. */
    static void draw(GuiGraphics graphics, int x, int y, float fill) {
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        // Legacy Vec3.rotateAroundZ uses the opposite sign to JOML's Z axis.
        // Empty points to the blue lower-left; full points to the red lower-right.
        pose.mulPose(Axis.ZP.rotationDegrees(Math.max(0, Math.min(1, fill)) * 270 + 45));
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

}
