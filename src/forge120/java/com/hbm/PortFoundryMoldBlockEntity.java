package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Small foundry mold: three ingots of one supported metal cool to one cast plate. */
public final class PortFoundryMoldBlockEntity extends BlockEntity {
    public static final int CAST_PLATE_QUANTA = 216;
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override protected void onContentsChanged(int slot) { visualStateChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(PortItems.MOLD_PLATE_CAST.get());
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new SidedItems(items));
    private int moltenAmount;
    private PortMaterial material = PortMaterial.COPPER;
    private int cooloff = 200;
    private boolean displayMold;
    private boolean displayCasting;

    public PortFoundryMoldBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.FOUNDRY_MOLD.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public int copperQuanta() { return material == PortMaterial.COPPER ? moltenAmount : 0; }
    public int amount() { return moltenAmount; }
    public PortMaterial material() { return material; }
    public int cooloff() { return cooloff; }
    public boolean displayMold() { return displayMold; }
    public boolean displayCasting() { return displayCasting; }

    private void visualStateChanged() {
        setChanged();
        if (level != null && !level.isClientSide && !isRemoved()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Display state is separate from the authoritative inventory and cooling timer.
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("displayMold", items.getStackInSlot(0).is(PortItems.MOLD_PLATE_CAST.get()));
        ItemStack output = items.getStackInSlot(1);
        tag.putBoolean("displayCasting", !output.isEmpty());
        PortMaterial displayed = material;
        for (PortMaterial candidate : PortMaterial.values()) {
            if (!candidate.castPlate().isEmpty() && output.is(candidate.castPlate().getItem())) displayed = candidate;
        }
        tag.putString("material", displayed.key());
        tag.putInt("amount", moltenAmount);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        displayMold = tag.getBoolean("displayMold");
        displayCasting = tag.getBoolean("displayCasting");
        try { material = PortMaterial.byKey(tag.getString("material")); }
        catch (IllegalArgumentException invalid) { material = PortMaterial.COPPER; }
        moltenAmount = Math.max(0, Math.min(CAST_PLATE_QUANTA, tag.getInt("amount")));
    }

    ItemStack takeScraps() {
        ItemStack recovered = PortScrapsItem.of(material, moltenAmount);
        moltenAmount = 0;
        cooloff = 200;
        if (!recovered.isEmpty()) visualStateChanged();
        else setChanged();
        return recovered;
    }

    public int acceptCopper(int offered) {
        return acceptMaterial(PortMaterial.COPPER, offered);
    }
    public int acceptMaterial(PortMaterial offeredMaterial, int offered) {
        if (offered <= 0 || !items.getStackInSlot(0).is(PortItems.MOLD_PLATE_CAST.get())
                || !items.getStackInSlot(1).isEmpty() || offeredMaterial.castPlate().isEmpty()
                || moltenAmount > 0 && material != offeredMaterial) return 0;
        int accepted = Math.min(offered, CAST_PLATE_QUANTA - moltenAmount);
        if (accepted > 0) {
            material = offeredMaterial;
            moltenAmount += accepted;
            visualStateChanged();
        }
        return accepted;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortFoundryMoldBlockEntity machine) {
        if (level.isClientSide) return;
        if (machine.moltenAmount == CAST_PLATE_QUANTA
                && machine.items.getStackInSlot(0).is(PortItems.MOLD_PLATE_CAST.get())
                && machine.items.getStackInSlot(1).isEmpty()) {
            machine.cooloff--;
            if (machine.cooloff <= 0) {
                machine.moltenAmount = 0;
                machine.items.setStackInSlot(1, machine.material.castPlate());
                machine.cooloff = 200;
            }
            machine.setChanged();
        } else if (machine.cooloff != 200) {
            machine.cooloff = 200;
            machine.setChanged();
        }
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        moltenAmount = Math.max(0, Math.min(CAST_PLATE_QUANTA, tag.contains("amount", 3) ? tag.getInt("amount") : tag.getInt("copperQuanta")));
        try { material = PortMaterial.byKey(tag.getString("material")); }
        catch (IllegalArgumentException invalid) { material = PortMaterial.COPPER; }
        cooloff = Math.max(1, Math.min(200, tag.getInt("cooloff")));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("amount", moltenAmount);
        tag.putString("material", material.key());
        tag.putInt("cooloff", cooloff);
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCap = LazyOptional.of(() -> new SidedItems(items)); }

    private record SidedItems(ItemStackHandler backing) implements IItemHandler {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int slot) { return backing.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // The source exposes only its finished casting to automation.
            return stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? backing.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return backing.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
