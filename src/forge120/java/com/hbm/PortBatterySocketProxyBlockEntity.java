package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/** Legacy socket proxies expose the one core inventory without owning duplicate items. */
public final class PortBatterySocketProxyBlockEntity extends BlockEntity {
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> new ProxyItems(this));

    public PortBatterySocketProxyBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BATTERY_SOCKET_PROXY.get(), pos, state);
    }

    private IItemHandler coreItems() {
        Level level = getLevel();
        BlockState state = getBlockState();
        if (level == null || isRemoved() || !(state.getBlock() instanceof PortBatterySocketBlock)
                || state.getValue(PortBatterySocketBlock.PART) == 0) return null;
        BlockPos core = PortBatterySocketBlock.corePos(worldPosition, state);
        if (!level.hasChunkAt(core)) return null;
        BlockState coreState = level.getBlockState(core);
        if (!(coreState.getBlock() instanceof PortBatterySocketBlock)
                || coreState.getValue(PortBatterySocketBlock.PART) != 0
                || coreState.getValue(PortBatterySocketBlock.FACING) != state.getValue(PortBatterySocketBlock.FACING)
                || !(level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket)) return null;
        return socket.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> new ProxyItems(this));
    }

    private record ProxyItems(PortBatterySocketProxyBlockEntity proxy) implements IItemHandler {
        @Override
        public int getSlots() {
            IItemHandler core = proxy.coreItems();
            return core == null ? 0 : core.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler core = proxy.coreItems();
            return core == null ? ItemStack.EMPTY : core.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler core = proxy.coreItems();
            return core == null ? stack : core.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler core = proxy.coreItems();
            return core == null ? ItemStack.EMPTY : core.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler core = proxy.coreItems();
            return core == null ? 0 : core.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler core = proxy.coreItems();
            return core != null && core.isItemValid(slot, stack);
        }
    }
}
