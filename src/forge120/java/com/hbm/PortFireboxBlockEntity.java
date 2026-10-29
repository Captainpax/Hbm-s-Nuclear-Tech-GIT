package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Solid-fuel firebox heat source for the crucible. */
public final class PortFireboxBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return fuelTime(stack) > 0; }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);
    private int burnTime;
    private int maxBurnTime;
    private int burnHeat;
    private int heatEnergy;
    private int smoke;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> heatEnergy & 0xffff;
                case 3 -> heatEnergy >>> 16;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };

    public PortFireboxBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.FIREBOX.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public int heatEnergy() { return heatEnergy; }
    public int burnTime() { return burnTime; }
    public int smoke() { return smoke; }
    public ContainerData menuData() { return menuData; }

    public void useUpHeat(int amount) {
        heatEnergy = Math.max(0, heatEnergy - Math.max(0, amount));
        setChanged();
    }

    public static int fuelTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortFireboxBlockEntity machine) {
        if (!level.isClientSide) machine.tickServer();
    }

    private void tickServer() {
        pushSmoke();
        boolean wasBurning = burnTime > 0;
        if (burnTime <= 0) {
            for (int slot = 0; slot < 2; slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                int base = fuelTime(stack);
                if (base > 0) {
                    boolean coal = stack.is(Items.COAL) || stack.is(Items.CHARCOAL)
                            || stack.is(PortItems.LIGNITE.get());
                    maxBurnTime = burnTime = coal ? base * 5 / 4 : base;
                    burnHeat = coal ? 200 : 100;
                    ItemStack remainder = stack.getCraftingRemainingItem();
                    if (stack.getCount() == 1 && !remainder.isEmpty()) items.setStackInSlot(slot, remainder);
                    else items.extractItem(slot, 1, false);
                    break;
                }
            }
        }
        if (burnTime > 0) {
            // A newly lit fuel item heats immediately but starts consuming burn
            // ticks (and producing smoke) on the next tick in TileEntityFireboxBase.
            if (wasBurning && heatEnergy < 100_000) {
                burnTime--;
                if (level.getGameTime() % 20 == 0) {
                    int generated = (int) Math.ceil(PortSootPollution.SOOT_PER_SECOND * 3 * 100);
                    int overflow = Math.max(0, smoke + generated - 50);
                    smoke = Math.min(50, smoke + generated);
                    if (overflow > 0 && level instanceof ServerLevel server) {
                        PortSootPollution.get(server).add(worldPosition, overflow / 100F);
                    }
                }
            }
            heatEnergy = Math.min(100_000, heatEnergy + burnHeat);
        } else {
            heatEnergy = Math.max(0, heatEnergy - Math.max(heatEnergy / 1000, 1));
            burnHeat = 0;
        }
        if (burnTime > 0 || heatEnergy > 0) setChanged();
        if (wasBurning != (burnTime > 0)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void pushSmoke() {
        if (smoke == 0) return;
        // Twelve source outlets: three positions along each horizontal face.
        for (Direction outward : Direction.Plane.HORIZONTAL) {
            Direction tangent = outward.getClockWise();
            for (int offset = -1; offset <= 1; offset++) {
                BlockPos target = worldPosition.relative(outward, 2).relative(tangent, offset);
                if (!level.hasChunkAt(target)) continue;
                BlockEntity receiver = level.getBlockEntity(target);
                if (receiver == null) continue;
                IFluidHandler handler = receiver.getCapability(ForgeCapabilities.FLUID_HANDLER,
                        outward.getOpposite()).resolve().orElse(null);
                if (handler == null) continue;
                int sent = handler.fill(new FluidStack(PortFluids.SMOKE.get(), smoke), IFluidHandler.FluidAction.EXECUTE);
                sent = Math.max(0, Math.min(smoke, sent));
                if (sent > 0) { smoke -= sent; setChanged(); }
                if (smoke == 0) return;
            }
        }
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        burnTime = Math.max(0, tag.getInt("burnTime"));
        maxBurnTime = Math.max(0, tag.getInt("maxBurnTime"));
        burnHeat = Math.max(0, tag.getInt("burnHeat"));
        heatEnergy = Math.max(0, Math.min(100_000, tag.getInt("heatEnergy")));
        smoke = Math.max(0, Math.min(50, tag.getInt("smoke")));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("burnTime", burnTime);
        tag.putInt("maxBurnTime", maxBurnTime);
        tag.putInt("burnHeat", burnHeat);
        tag.putInt("heatEnergy", heatEnergy);
        tag.putInt("smoke", smoke);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.hbm.firebox"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortFireboxMenu(id, inventory, this);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCap = LazyOptional.of(() -> items); }
}
