package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Source anvil background with the first two tier-1 construction choices. */
public final class PortAnvilScreen extends AbstractContainerScreen<PortAnvilMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/processing/gui_anvil.png");

    public PortAnvilScreen(PortAnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("item.hbm.stamp_iron_plate"),
                button -> craft(0, false)).tooltip(requirements(0)).bounds(leftPos + 8, topPos + 52, 120, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("item.hbm.stamp_iron_wire"),
                button -> craft(1, false)).tooltip(requirements(1)).bounds(leftPos + 8, topPos + 67, 120, 14).build());
        addRenderableWidget(Button.builder(Component.literal("×64"),
                button -> craft(0, true)).bounds(leftPos + 131, topPos + 52, 37, 14).build());
        addRenderableWidget(Button.builder(Component.literal("×64"),
                button -> craft(1, true)).bounds(leftPos + 131, topPos + 67, 37, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("block.hbm.machine_blast_furnace"),
                button -> craft(2, false)).tooltip(requirements(2)).bounds(leftPos + 8, topPos + 82, 160, 14).build());
        if (menu.tier() >= 2) {
            addRenderableWidget(Button.builder(Component.translatable("block.hbm.heater_firebox"),
                    button -> craft(3, false)).tooltip(requirements(3)).bounds(leftPos + 8, topPos + 97, 160, 14).build());
            addRenderableWidget(Button.builder(Component.translatable("block.hbm.machine_crucible"),
                    button -> craft(4, false)).tooltip(requirements(4)).bounds(leftPos + 8, topPos + 112, 160, 14).build());
        }
    }

    private void craft(int recipe, boolean bulk) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, recipe * 2 + (bulk ? 1 : 0));
        }
    }

    private static Tooltip requirements(int index) {
        var text = Component.empty();
        for (var requirement : PortAnvilConstruction.recipes().get(index).requirements()) {
            if (!text.getString().isEmpty()) text.append("\n");
            text.append(requirement.count() + " × ").append(requirement.item().get().getDescription());
        }
        return Tooltip.create(text);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, imageWidth / 2, 6, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
