package com.hbm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Source construction grid, selection blueprint and inventory-backed crafting. */
public final class PortAnvilScreen extends AbstractContainerScreen<PortAnvilMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/gui/processing/gui_anvil.png");
    private final List<Integer> recipes = new ArrayList<>();
    private EditBox search;
    private int selected = -1, rowOffset;

    public PortAnvilScreen(PortAnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
    }

    @Override protected void init() {
        super.init();
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 10, topPos + 111, 84, 12,
                Component.translatable("gui.hbm.anvil.search"));
        search.setBordered(false);
        search.setTextColor(0xFFFFFF);
        search.setHint(Component.translatable("gui.hbm.anvil.search"));
        search.setMaxLength(25);
        search.setResponder(this::filter);
        search.setValue(previous);
        addRenderableWidget(search);
        filter(previous);
    }

    private void filter(String query) {
        recipes.clear();
        String match = query.toLowerCase(Locale.ROOT);
        var all = PortAnvilConstruction.recipes();
        for (int i = 0; i < all.size(); i++) {
            var recipe = all.get(i);
            if (recipe.tier() <= menu.tier() && matches(recipe, match)) recipes.add(i);
        }
        selected = -1;
        rowOffset = 0;
    }

    private static boolean matches(PortAnvilConstruction.Recipe recipe, String match) {
        if (match.isEmpty() || recipe.output().get().getHoverName().getString().toLowerCase(Locale.ROOT).contains(match)) return true;
        for (var input : recipe.requirements()) {
            if (input.item().get().getDescription().getString().toLowerCase(Locale.ROOT).contains(match)) return true;
            if (input.tag() != null && BuiltInRegistries.ITEM.getTag(input.tag()).stream()
                    .flatMap(holders -> holders.stream()).anyMatch(holder -> holder.value().getDescription().getString()
                            .toLowerCase(Locale.ROOT).contains(match))) return true;
        }
        return false;
    }

    private int lastRow() { return Math.max(0, (recipes.size() - 9) / 2); }

    private void clickSound() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean over(double x, double y, int left, int top, int w, int h) {
        return x >= leftPos + left && x < leftPos + left + w && y >= topPos + top && y < topPos + top + h;
    }

    private int recipeAt(double x, double y) {
        for (int visible = 0; visible < 10 && rowOffset * 2 + visible < recipes.size(); visible++) {
            if (over(x, y, 16 + 18 * (visible / 2), 71 + 18 * (visible % 2), 18, 18)) {
                return recipes.get(rowOffset * 2 + visible);
            }
        }
        return -1;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            int recipe = recipeAt(x, y);
            if (recipe >= 0) {
                selected = selected == recipe ? -1 : recipe;
                search.setFocused(false);
                clickSound();
                return true;
            }
            if (over(x, y, 52, 53, 18, 18)) {
                if (selected >= 0 && minecraft != null && minecraft.gameMode != null) {
                    clickSound();
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected * 2 + (hasShiftDown() ? 1 : 0));
                }
                return true;
            }
            if (over(x, y, 7, 71, 9, 36)) { rowOffset = Math.max(0, rowOffset - 1); clickSound(); return true; }
            if (over(x, y, 106, 71, 9, 36)) { rowOffset = Math.min(lastRow(), rowOffset + 1); clickSound(); return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (over(x, y, 7, 53, 108, 54)) {
            rowOffset = Math.max(0, Math.min(lastRow(), rowOffset + (delta > 0 ? -1 : delta < 0 ? 1 : 0)));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Typing an inventory-bound letter into search must not close the menu.
        if (search.isFocused() && key != 256) {
            return search.keyPressed(key, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override protected void containerTick() { super.containerTick(); search.tick(); }

    private record BlueprintLine(Component text, int color) {}

    private List<BlueprintLine> blueprint() {
        var lines = new ArrayList<BlueprintLine>();
        if (selected < 0) return lines;
        var recipe = PortAnvilConstruction.recipes().get(selected);
        lines.add(new BlueprintLine(Component.translatable("gui.hbm.anvil.inputs"), 0xFFFF55));
        for (var requirement : recipe.requirements()) {
            int owned = 0;
            for (int slot = 3; slot < menu.slots.size(); slot++) {
                var stack = menu.getSlot(slot).getItem();
                if (requirement.matches(stack)) owned += stack.getCount();
            }
            lines.add(new BlueprintLine(Component.literal(">" + requirement.count() + " × ")
                    .append(requirement.item().get().getDescription()), owned >= requirement.count() ? 0xFFFFFF : 0xFF5555));
        }
        lines.add(new BlueprintLine(Component.empty(), 0xFFFFFF));
        lines.add(new BlueprintLine(Component.translatable("gui.hbm.anvil.outputs"), 0xFFFF55));
        var output = recipe.output().get();
        lines.add(new BlueprintLine(Component.literal(">" + output.getCount() + " × ").append(output.getHoverName()), 0xFFFFFF));
        return lines;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        var lines = blueprint();
        int panelWidth = Math.max(54, (lines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0) + 1) / 2 + 10);
        // Reuse the source panel's left slice and right border as it expands.
        for (int x = 0; x < panelWidth - 3; x += 51) {
            graphics.blit(TEXTURE, leftPos + 125 + x, topPos + 17, 125, 17, Math.min(51, panelWidth - 3 - x), 108);
        }
        graphics.blit(TEXTURE, leftPos + 125 + panelWidth - 3, topPos + 17, 176, 17, 3, 108);
        if (search.isFocused()) graphics.blit(TEXTURE, leftPos + 8, topPos + 108, 168, 222, 88, 16);
        if (over(mouseX, mouseY, 52, 53, 18, 18)) graphics.blit(TEXTURE, leftPos + 52, topPos + 53, 176, 150, 18, 18);
        if (over(mouseX, mouseY, 7, 71, 9, 36)) graphics.blit(TEXTURE, leftPos + 7, topPos + 71, 176, 186, 9, 36);
        if (over(mouseX, mouseY, 106, 71, 9, 36)) graphics.blit(TEXTURE, leftPos + 106, topPos + 71, 185, 186, 9, 36);
        for (int visible = 0; visible < 10 && rowOffset * 2 + visible < recipes.size(); visible++) {
            int index = recipes.get(rowOffset * 2 + visible);
            int x = leftPos + 16 + 18 * (visible / 2), y = topPos + 71 + 18 * (visible % 2);
            var display = PortAnvilConstruction.recipes().get(index).output().get();
            graphics.renderItem(display, x + 1, y + 1);
            graphics.renderItemDecorations(font, display, x + 1, y + 1);
            if (selected == index) graphics.blit(TEXTURE, x, y, 0, 222, 18, 18);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, Component.translatable("gui.hbm.anvil.title", menu.tier()), 61, 8, 0x404040);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        int y = 50;
        for (var line : blueprint()) {
            graphics.drawString(font, line.text(), 260, y, line.color(), false);
            y += 9;
        }
        graphics.pose().popPose();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int recipe = recipeAt(mouseX, mouseY);
        if (recipe >= 0) graphics.renderTooltip(font, PortAnvilConstruction.recipes().get(recipe).output().get(), mouseX, mouseY);
        if (over(mouseX, mouseY, 52, 53, 18, 18)) graphics.renderTooltip(font,
                Component.translatable("gui.hbm.anvil.craft"), mouseX, mouseY);
    }
}
