package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

/** Copper melting and source waste-spout pouring for the first foundry route. */
public final class PortCrucibleBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_HEAT = 100_000;
    public static final int PROCESS_HEAT = 20_000;
    public static final int COPPER_QUANTA = 72;
    private final ItemStackHandler items = new ItemStackHandler(10) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 1 && stack.is(PortItems.INGOT_COPPER.get());
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new RangedWrapper(items, 1, 10));
    private int heat;
    private int progress;
    private int moltenCopper;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> heat & 0xffff;
                case 1 -> heat >>> 16;
                case 2 -> progress;
                case 3 -> moltenCopper;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };

    public PortCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.CRUCIBLE.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public int heat() { return heat; }
    public int progress() { return progress; }
    public int moltenCopper() { return moltenCopper; }
    public ContainerData menuData() { return menuData; }

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
        } else {
            heat = Math.max(0, heat - Math.max(heat / 1000, 1));
        }

        if (level.getGameTime() % 5 == 0) collectDroppedItems();

        int slot = -1;
        for (int index = 1; index < 10; index++) {
            if (items.getStackInSlot(index).is(PortItems.INGOT_COPPER.get())) { slot = index; break; }
        }
        if (heat >= MAX_HEAT / 2 && slot != -1 && moltenCopper <= 10_368 - COPPER_QUANTA) {
            int delta = (heat - MAX_HEAT / 2) / 20;
            heat -= delta;
            progress += delta;
            if (progress >= PROCESS_HEAT) {
                progress = 0;
                items.extractItem(slot, 1, false);
                moltenCopper += COPPER_QUANTA;
            }
        } else {
            progress = 0;
        }

        // With no loaded alloy recipe, the source pours copper from its waste spout.
        Direction spout = getBlockState().getValue(PortCastingMachineBlock.FACING).getOpposite();
        BlockPos target = worldPosition.relative(spout, 2).below();
        if (moltenCopper > 0 && level instanceof ServerLevel server) {
            PortSootPollution.get(server).add(worldPosition, 0.04F / 20.0F);
        }
        if (moltenCopper > 0 && level.hasChunkAt(target)
                && level.getBlockEntity(target) instanceof PortFoundryMoldBlockEntity mold) {
            int accepted = mold.acceptCopper(Math.min(24, moltenCopper));
            moltenCopper -= accepted;
        }
        if (heat > 0 || progress > 0 || moltenCopper > 0) setChanged();
    }

    void collectDroppedItems() {
        // The source collects smeltable drops inside the basin every five ticks.
        // Copper is the only material supported by this first casting slice.
        AABB basin = new AABB(worldPosition.getX() - 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() - 0.5, worldPosition.getX() + 1.5,
                worldPosition.getY() + 1, worldPosition.getZ() + 1.5);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, basin, item -> item.isAlive())) {
            if (!entity.getItem().is(PortItems.INGOT_COPPER.get())) continue;
            ItemStack remaining = entity.getItem().copy();
            for (int slot = 1; slot < 10 && !remaining.isEmpty(); slot++) {
                if (!items.getStackInSlot(slot).isEmpty()) continue;
                items.setStackInSlot(slot, remaining.split(1));
            }
            if (remaining.isEmpty()) entity.discard();
            else entity.setItem(remaining);
        }
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        heat = Math.max(0, Math.min(MAX_HEAT, tag.getInt("heat")));
        progress = Math.max(0, Math.min(PROCESS_HEAT - 1, tag.getInt("progress")));
        moltenCopper = Math.max(0, Math.min(10_368, tag.getInt("moltenCopper")));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("heat", heat);
        tag.putInt("progress", progress);
        tag.putInt("moltenCopper", moltenCopper);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.hbm.crucible"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortCrucibleMenu(id, inventory, this);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCap.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> new RangedWrapper(items, 1, 10));
    }
}
