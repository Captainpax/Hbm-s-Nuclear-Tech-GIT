package com.hbm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PortCrucibleScreen extends AbstractContainerScreen<PortCrucibleMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/processing/gui_crucible.png");
    private static final ResourceLocation FOLDER = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/item/template_folder.png");
    public PortCrucibleScreen(PortCrucibleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 214;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int progress = Math.max(0, Math.min(33, menu.progress() * 33 / PortCrucibleBlockEntity.PROCESS_HEAT));
        int heat = Math.max(0, Math.min(33, menu.heat() * 33 / PortCrucibleBlockEntity.MAX_HEAT));
        graphics.blit(TEXTURE, leftPos + 126, topPos + 82, 176, 0, progress, 5);
        graphics.blit(TEXTURE, leftPos + 126, topPos + 91, 176, 5, heat, 5);
        PortCrucibleRecipe recipe = menu.selectedRecipe();
        if (recipe == null) graphics.blit(FOLDER, leftPos + 107, topPos + 81, 0, 0, 16, 16, 16, 16);
        else graphics.renderItem(recipe.getResultItem(minecraft.level.registryAccess()), leftPos + 107, topPos + 81);
        drawStore(graphics, false, 17);
        drawStore(graphics, true, 62);
    }
    private void drawStore(GuiGraphics graphics, boolean recipe, int x) {
        int height = 0, amount = 0;
        for (PortMaterial material : menu.materials(recipe)) {
            amount += menu.amount(recipe, material);
            int target = Math.min(79, amount * 79 / PortCrucibleBlockEntity.STORE_CAPACITY);
            if (target == height) continue;
            int color = material.moltenColor;
            // Combine the source opaque tint and white 30% additive glow in one shader pass.
            graphics.setColor(((color >> 16 & 255) + 77) / 255F,
                    ((color >> 8 & 255) + 77) / 255F, ((color & 255) + 77) / 255F, 1);
            graphics.blit(TEXTURE, leftPos + x, topPos + 97 - target,
                    material.castable ? 176 : 210, 89 - target, 34, target - height);
            graphics.setColor(1, 1, 1, 1);
            height = target;
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= leftPos + 106 && x < leftPos + 124 && y >= topPos + 80 && y < topPos + 98) {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    (menu.selectedIndex() + 1) % (menu.recipes().size() + 1));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0xffffff);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(16, 17, 36, 81, mouseX, mouseY)) storeTooltip(graphics, false, mouseX, mouseY);
        if (isHovering(61, 17, 36, 81, mouseX, mouseY)) storeTooltip(graphics, true, mouseX, mouseY);
        if (isHovering(125, 81, 34, 7, mouseX, mouseY)) graphics.renderTooltip(font,
                Component.translatable("container.hbm.crucible.progress", menu.progress(), PortCrucibleBlockEntity.PROCESS_HEAT), mouseX, mouseY);
        if (isHovering(125, 90, 34, 7, mouseX, mouseY)) graphics.renderTooltip(font,
                Component.translatable("container.hbm.crucible.heat", menu.heat(), PortCrucibleBlockEntity.MAX_HEAT), mouseX, mouseY);
        if (isHovering(106, 80, 18, 18, mouseX, mouseY)) {
            var recipe = menu.selectedRecipe();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("container.hbm.crucible.select", recipe == null
                    ? Component.translatable("container.hbm.crucible.none") : recipe.getResultItem(minecraft.level.registryAccess()).getHoverName()));
            if (recipe != null) {
                recipe.input().forEach((material, amount) -> tooltip.add(Component.translatable(
                        "container.hbm.crucible.input", material.title(), amount)));
                recipe.output().forEach((material, amount) -> tooltip.add(Component.translatable(
                        "container.hbm.crucible.output", material.title(), amount)));
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }
    private void storeTooltip(GuiGraphics graphics, boolean recipe, int x, int y) {
        List<Component> tooltip = new ArrayList<>();
        int total = 0;
        for (PortMaterial material : PortMaterial.values()) total += menu.amount(recipe, material);
        tooltip.add(Component.translatable(recipe ? "container.hbm.crucible.recipe" : "container.hbm.crucible.waste",
                total, PortCrucibleBlockEntity.STORE_CAPACITY));
        for (PortMaterial material : PortMaterial.values()) if (menu.amount(recipe, material) > 0) {
            tooltip.add(Component.translatable("container.hbm.crucible.material", material.title(), menu.amount(recipe, material)));
        }
        graphics.renderComponentTooltip(font, tooltip, x, y);
    }
}
