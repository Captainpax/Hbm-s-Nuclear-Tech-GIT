package com.hbm;

import api.hbm.block.ICrucibleAcceptor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

/** Source independent recipe/waste stores, material ratios, smelting and safe pours. */
public final class PortCrucibleBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_HEAT = 100_000;
    public static final int PROCESS_HEAT = 20_000;
    public static final int COPPER_QUANTA = PortMaterial.INGOT;
    public static final int STORE_CAPACITY = PortMaterial.BLOCK * 16;
    public static final int MAX_COPPER_QUANTA = STORE_CAPACITY;
    public static final int DATA_COUNT = 4 + PortMaterial.values().length * 4;
    private final PortMaterialStore recipeStore = new PortMaterialStore(STORE_CAPACITY, this::setChanged);
    private final PortMaterialStore wasteStore = new PortMaterialStore(STORE_CAPACITY, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(10) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot >= 1 && canSmelt(stack); }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new RangedWrapper(items, 1, 10));
    private int heat, progress, renderMass, lastSyncedMass = -1;
    private String selectedRecipe = "";
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            if (index == 0) return heat & 0xffff;
            if (index == 1) return heat >>> 16;
            if (index == 2) return progress;
            if (index == 3) return recipeIndex();
            int material = index - 4;
            if (material >= PortMaterial.values().length * 2 && material < PortMaterial.values().length * 4) {
                int order = material - PortMaterial.values().length * 2;
                var contents = (order < PortMaterial.values().length ? recipeStore : wasteStore).contents();
                int indexInStore = order % PortMaterial.values().length;
                return indexInStore < contents.size() ? contents.get(indexInStore).material().ordinal() + 1 : 0;
            }
            if (material < 0 || material >= PortMaterial.values().length * 2) return 0;
            PortMaterialStore store = material < PortMaterial.values().length ? recipeStore : wasteStore;
            return store.amount(PortMaterial.values()[material % PortMaterial.values().length]);
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };

    public PortCrucibleBlockEntity(BlockPos pos, BlockState state) { super(PortBlockEntities.CRUCIBLE.get(), pos, state); }
    public ItemStackHandler items() { return items; }
    public int heat() { return heat; }
    public int progress() { return progress; }
    public int moltenCopper() { return recipeStore.amount(PortMaterial.COPPER) + wasteStore.amount(PortMaterial.COPPER); }
    public int totalMolten() { return recipeStore.total() + wasteStore.total(); }
    public int renderMass() { return renderMass; }
    public ContainerData menuData() { return menuData; }
    public PortMaterialStore recipeStore() { return recipeStore; }
    public PortMaterialStore wasteStore() { return wasteStore; }
    public String selectedRecipe() { return selectedRecipe; }
    public PortCrucibleRecipe loadedRecipe() { return level == null ? null : PortCrucibleRecipes.find(level, selectedRecipe); }

    private int recipeIndex() {
        if (level == null) return 0;
        var recipes = PortCrucibleRecipes.all(level);
        for (int index = 0; index < recipes.size(); index++) if (recipes.get(index).getId().toString().equals(selectedRecipe)) return index + 1;
        return 0;
    }
    public boolean selectRecipe(String selection) {
        if (!selection.isEmpty() && (level == null || PortCrucibleRecipes.find(level, selection) == null)) return false;
        selectedRecipe = selection;
        setChanged();
        return true;
    }
    public boolean canSmelt(ItemStack stack) {
        List<PortMaterialStack> materials = PortMaterial.fromItem(stack);
        if (materials.isEmpty()) return false;
        PortCrucibleRecipe recipe = loadedRecipe();
        boolean matches = recipe == null;
        long recipeAmount = recipeStore.total(), wasteAmount = wasteStore.total();
        for (PortMaterialStack material : materials) {
            int required = recipe == null ? 0 : recipe.input().getOrDefault(material.material(), 0);
            if (recipe != null && recipe.output().containsKey(material.material())) {
                recipeAmount += material.amount();
                matches = true;
            } else if (required == 0) {
                wasteAmount += material.amount();
            } else {
                long maximum = (long) required * STORE_CAPACITY / recipe.inputAmount();
                if ((long) recipeStore.amount(material.material()) + material.amount() > maximum) return false;
                recipeAmount += material.amount();
                matches = true;
            }
        }
        return matches && recipeAmount <= STORE_CAPACITY && wasteAmount <= STORE_CAPACITY;
    }
    public int acceptPour(PortMaterial material, int offered) {
        if (offered <= 0) return 0;
        PortCrucibleRecipe recipe = loadedRecipe();
        int accepted;
        if (recipe == null) accepted = wasteStore.add(material, offered);
        else {
            int required = recipe.input().getOrDefault(material, 0);
            if (required == 0) return 0;
            int maximum = (int) ((long) required * STORE_CAPACITY / recipe.inputAmount());
            accepted = recipeStore.add(material, Math.min(offered, Math.max(0, maximum - recipeStore.amount(material))));
        }
        if (accepted > 0) syncRender(true);
        return accepted;
    }
    List<ItemStack> takeScraps() {
        List<ItemStack> scraps = new ArrayList<>(recipeStore.takeScraps());
        scraps.addAll(wasteStore.takeScraps());
        syncRender(true);
        return scraps;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, PortCrucibleBlockEntity machine) {
        if (!level.isClientSide) machine.tickServer();
    }
    private void tickServer() {
        BlockEntity below = level.getBlockEntity(worldPosition.below());
        if (below instanceof PortFireboxBlockEntity firebox && firebox.heatEnergy() > heat) {
            int delta = Math.min(MAX_HEAT - heat, firebox.heatEnergy() - heat);
            int transfer = Math.max(1, (delta + 3) / 4);
            firebox.useUpHeat(transfer);
            heat = Math.min(MAX_HEAT, heat + transfer);
        } else heat = Math.max(0, heat - Math.max(heat / 1000, 1));
        if (level.getGameTime() % 5 == 0) collectDroppedItems();
        int slot = -1;
        for (int index = 1; index < 10; index++) if (canSmelt(items.getStackInSlot(index))) { slot = index; break; }
        if (heat >= MAX_HEAT / 2 && slot != -1) {
            int delta = (heat - MAX_HEAT / 2) / 20;
            heat -= delta;
            progress += delta;
            if (progress >= PROCESS_HEAT) {
                progress = 0;
                PortCrucibleRecipe recipe = loadedRecipe();
                for (PortMaterialStack material : PortMaterial.fromItem(items.getStackInSlot(slot))) {
                    (recipe != null && recipe.uses(material.material()) ? recipeStore : wasteStore).add(material.material(), material.amount());
                }
                items.extractItem(slot, 1, false);
            }
        } else progress = 0;
        PortCrucibleRecipe recipe = loadedRecipe();
        if (recipe != null) recipe.apply(recipeStore, level.getGameTime());
        Direction front = getBlockState().getValue(PortCastingMachineBlock.FACING);
        pourStore(wasteStore, front.getOpposite(), null);
        pourStore(recipeStore, front, recipe);
        syncRender(false);
        if (heat > 0 || progress > 0) setChanged();
    }
    private void pourStore(PortMaterialStore store, Direction direction, PortCrucibleRecipe recipe) {
        if (store.total() == 0) return;
        if (level instanceof ServerLevel server) PortSootPollution.get(server).add(worldPosition, 0.04F / 20.0F);
        Vec3 start = new Vec3(worldPosition.getX() + 0.5 + direction.getStepX() * 1.875,
                worldPosition.getY() + 0.25, worldPosition.getZ() + 0.5 + direction.getStepZ() * 1.875);
        // A vertical ray stays in one chunk; never generate/load a missing target chunk.
        if (!level.hasChunkAt(BlockPos.containing(start))) return;
        var hit = level.clip(new ClipContext(start, start.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        if (hit.getType() != HitResult.Type.BLOCK || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof ICrucibleAcceptor acceptor)) return;
        for (PortMaterialStack material : store.contents()) {
            if (!material.material().castable || recipe != null && !recipe.output().containsKey(material.material())) continue;
            int offered = Math.min(24, material.amount());
            int accepted = acceptor.acceptPour(level, hit.getBlockPos(), material.material(), offered);
            if (accepted > 0) { store.remove(material.material(), Math.min(offered, accepted)); break; }
        }
    }
    void collectDroppedItems() {
        AABB basin = new AABB(worldPosition.getX() - 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() - 0.5,
                worldPosition.getX() + 1.5, worldPosition.getY() + 1, worldPosition.getZ() + 1.5);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, basin, ItemEntity::isAlive)) {
            if (!canSmelt(entity.getItem())) continue;
            ItemStack remaining = entity.getItem().copy();
            for (int slot = 1; slot < 10 && !remaining.isEmpty(); slot++) {
                if (items.getStackInSlot(slot).isEmpty()) items.setStackInSlot(slot, remaining.split(1));
            }
            if (remaining.isEmpty()) entity.discard(); else entity.setItem(remaining);
        }
    }
    private void syncRender(boolean immediate) {
        if (level == null || level.isClientSide) return;
        int mass = totalMolten();
        if (mass != lastSyncedMass && (immediate || mass == 0 || lastSyncedMass <= 0 || level.getGameTime() % 25 == 0)) {
            lastSyncedMass = mass;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("renderMass", totalMolten());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    @Override public void handleUpdateTag(CompoundTag tag) { renderMass = Math.max(0, Math.min(STORE_CAPACITY * 2, tag.getInt("renderMass"))); }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", Tag.TAG_COMPOUND)) items.deserializeNBT(tag.getCompound("items"));
        heat = Math.max(0, Math.min(MAX_HEAT, tag.getInt("heat")));
        progress = Math.max(0, Math.min(PROCESS_HEAT - 1, tag.getInt("progress")));
        selectedRecipe = tag.getString("selectedRecipe");
        recipeStore.load(tag.getList("recipeMaterials", Tag.TAG_COMPOUND));
        wasteStore.load(tag.getList("wasteMaterials", Tag.TAG_COMPOUND));
        if (!tag.contains("wasteMaterials", Tag.TAG_LIST)) wasteStore.add(PortMaterial.COPPER, tag.getInt("moltenCopper"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("heat", heat);
        tag.putInt("progress", progress);
        tag.putString("selectedRecipe", selectedRecipe);
        tag.put("recipeMaterials", recipeStore.save());
        tag.put("wasteMaterials", wasteStore.save());
    }
    @Override public Component getDisplayName() { return Component.translatable("container.hbm.crucible"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new PortCrucibleMenu(id, inventory, this); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCap = LazyOptional.of(() -> new RangedWrapper(items, 1, 10)); }
}
