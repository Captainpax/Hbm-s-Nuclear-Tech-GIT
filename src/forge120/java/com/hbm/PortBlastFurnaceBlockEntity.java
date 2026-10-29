package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

/** Source five-slot blast furnace: 800 fuel per 800-duration steel operation at half speed. */
public final class PortBlastFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_RATE = 800;
    public static final int MAX_FUEL = 38_400;
    private final ItemStackHandler items = new ItemStackHandler(5) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && fuelTime(stack) > 0 || slot == 1 || slot == 2;
        }
    };
    private LazyOptional<IItemHandler> fullItems = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> sideItems = LazyOptional.of(() -> new SidedItems(items));
    private int fuel;
    private int progress;
    private int progressRemainder;
    private int airblast;
    private int flueGas;
    private LazyOptional<IFluidHandler> fluids = LazyOptional.of(GasHandler::new);
    private boolean progressing;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> fuel & 0xffff;
                case 1 -> fuel >>> 16;
                case 2 -> progress;
                case 3 -> flueGas;
                case 4 -> progressing ? 1 : 0;
                case 5 -> airblast;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 6; }
    };

    public PortBlastFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BLAST_FURNACE.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public int fuel() { return fuel; }
    public int progress() { return progress; }
    public int flueGas() { return flueGas; }
    public int airblast() { return airblast; }
    public boolean progressing() { return progressing; }
    public ContainerData menuData() { return menuData; }

    public static int fuelTime(ItemStack stack) {
        if (stack.isEmpty() || !stack.getCraftingRemainingItem().isEmpty()) return 0;
        // The source burn module removes wood heat. Coal, charcoal and lignite
        // are the Survival fuels available in this progression slice.
        if (!stack.is(Items.COAL) && !stack.is(Items.CHARCOAL)
                && !stack.is(PortItems.LIGNITE.get())) return 0;
        return Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortBlastFurnaceBlockEntity machine) {
        if (!level.isClientSide) machine.tickServer();
    }

    private void tickServer() {
        pushFlue();
        ItemStack fuelStack = items.getStackInSlot(0);
        int burn = fuelTime(fuelStack);
        if (burn > 0 && fuel <= MAX_FUEL - burn) {
            fuel += burn;
            items.extractItem(0, 1, false);
        }
        boolean canRun = fuel >= FUEL_RATE
                && PortBlastFurnaceRecipes.steelFromIngots(items.getStackInSlot(1), items.getStackInSlot(2))
                && canOutput(3, new ItemStack(PortItems.INGOT_STEEL.get(), 2))
                && canOutput(4, new ItemStack(PortItems.INGOT_RAW_SLAG.get()));
        progressing = canRun;
        if (canRun) {
            // Progress is in half-ticks; keep fractional airblast speed exactly
            // to 1/1000 of a half-tick instead of truncating it each tick.
            progressRemainder += Math.min(10_000, 1_000 + airblast * 4);
            progress += progressRemainder / 1_000;
            progressRemainder %= 1_000;
            if (progress >= PortBlastFurnaceRecipes.STEEL_DURATION * 2) {
                int ironSlot = PortBlastFurnaceRecipes.ironSlot(items.getStackInSlot(1), items.getStackInSlot(2));
                int sandSlot = ironSlot == 1 ? 2 : 1;
                items.extractItem(ironSlot, 2, false);
                items.extractItem(sandSlot, 1, false);
                putOutput(3, new ItemStack(PortItems.INGOT_STEEL.get(), 2));
                putOutput(4, new ItemStack(PortItems.INGOT_RAW_SLAG.get()));
                fuel -= FUEL_RATE;
                int spill = Math.max(0, flueGas + 100 - 1_000);
                flueGas = Math.min(1_000, flueGas + 100);
                if (spill > 0 && level instanceof ServerLevel server) {
                    // FLUE's release trait plus the source explicit spill call.
                    PortSootPollution.get(server).add(worldPosition, 0.005F * (spill + 1));
                }
                progress = 0;
                progressRemainder = 0;
            }
        } else {
            progress = 0;
            progressRemainder = 0;
        }
        if (airblast > 0) {
            airblast = airblast * 95 / 100;
            setChanged();
        }
        if (canRun || burn > 0) setChanged();
    }

    private void pushFlue() {
        if (flueGas <= 0) return;
        int rate = Math.max(flueGas * 50 / 1_000, 1);
        for (BlockPos port : PortBlastFurnaceBlock.fluidPorts(worldPosition, getBlockState())) {
            if (!level.hasChunkAt(port)) continue;
            Direction outward = PortBlastFurnaceBlock.portDirection(level.getBlockState(port));
            if (outward == null) continue;
            BlockPos target = port.relative(outward);
            if (!level.hasChunkAt(target)) continue;
            BlockEntity receiver = level.getBlockEntity(target);
            if (receiver == null) continue;
            IFluidHandler handler = receiver.getCapability(ForgeCapabilities.FLUID_HANDLER,
                    outward.getOpposite()).resolve().orElse(null);
            if (handler == null) continue;
            int sent = handler.fill(new FluidStack(PortFluids.FLUE.get(), Math.min(rate, flueGas)),
                    IFluidHandler.FluidAction.EXECUTE);
            if (sent > 0) { flueGas -= sent; setChanged(); }
            if (flueGas == 0) break;
        }
    }

    private boolean canOutput(int slot, ItemStack output) {
        ItemStack current = items.getStackInSlot(slot);
        return current.isEmpty() || ItemStack.isSameItemSameTags(current, output)
                && current.getCount() + output.getCount() <= current.getMaxStackSize();
    }

    private void putOutput(int slot, ItemStack output) {
        ItemStack current = items.getStackInSlot(slot);
        if (current.isEmpty()) items.setStackInSlot(slot, output);
        else {
            ItemStack grown = current.copy();
            grown.grow(output.getCount());
            items.setStackInSlot(slot, grown);
        }
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        fuel = Math.max(0, Math.min(MAX_FUEL, tag.getInt("fuel")));
        progress = Math.max(0, Math.min(PortBlastFurnaceRecipes.STEEL_DURATION * 2 - 1, tag.getInt("progress")));
        progressRemainder = Math.max(0, Math.min(999, tag.getInt("progressRemainder")));
        airblast = Math.max(0, Math.min(4_000, tag.getInt("airblast")));
        flueGas = Math.max(0, Math.min(1_000, tag.getInt("flueGas")));
        progressing = tag.getBoolean("progressing");
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("fuel", fuel);
        tag.putInt("progress", progress);
        tag.putInt("progressRemainder", progressRemainder);
        tag.putInt("airblast", airblast);
        tag.putInt("flueGas", flueGas);
        tag.putBoolean("progressing", progressing);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.hbm.blast_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortBlastFurnaceMenu(id, inventory, this);
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return (side == null ? fullItems : sideItems).cast();
        if (!isRemoved() && cap == ForgeCapabilities.FLUID_HANDLER && side == null) return fluids.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fullItems.invalidate();
        sideItems.invalidate();
        fluids.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps();
        fullItems = LazyOptional.of(() -> items);
        sideItems = LazyOptional.of(() -> new SidedItems(items));
        fluids = LazyOptional.of(GasHandler::new);
    }

    private final class GasHandler implements IFluidHandler {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? (airblast == 0 ? FluidStack.EMPTY : new FluidStack(PortFluids.AIRBLAST.get(), airblast))
                    : (flueGas == 0 ? FluidStack.EMPTY : new FluidStack(PortFluids.FLUE.get(), flueGas));
        }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? 4_000 : 1_000; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.getFluid() == PortFluids.AIRBLAST.get();
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (stack.isEmpty() || !isFluidValid(0, stack)) return 0;
            int accepted = Math.min(4_000 - airblast, stack.getAmount());
            if (action.execute() && accepted > 0) { airblast += accepted; setChanged(); }
            return accepted;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return stack.getFluid() == PortFluids.FLUE.get() ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            int extracted = Math.min(flueGas, Math.max(0, amount));
            if (extracted == 0) return FluidStack.EMPTY;
            if (action.execute()) { flueGas -= extracted; setChanged(); }
            return new FluidStack(PortFluids.FLUE.get(), extracted);
        }
    }

    private record SidedItems(ItemStackHandler backing) implements IItemHandler {
        @Override public int getSlots() { return 5; }
        @Override public ItemStack getStackInSlot(int slot) { return backing.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < 3 ? backing.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= 3 ? backing.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return backing.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot < 3 && backing.isItemValid(slot, stack);
        }
    }
}
