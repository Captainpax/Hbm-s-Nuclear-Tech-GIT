package com.hbm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortPressSoundGameTests {
    private PortPressSoundGameTests() {}

    @GameTest(template = "empty")
    public static void sourceFireboxAndBlastCrackleOnlyWhileActive(GameTestHelper helper) {
        var level = helper.getLevel();
        var firePos = helper.absolutePos(new BlockPos(1, 2, 1));
        var blastPos = helper.absolutePos(new BlockPos(3, 2, 3));
        var fireState = PortBlocks.HEATER_FIREBOX.get().defaultBlockState();
        var blastState = PortBlocks.MACHINE_BLAST_FURNACE.get().defaultBlockState();
        level.setBlock(firePos, fireState, 3);
        level.setBlock(blastPos, blastState, 3);
        var firebox = (PortFireboxBlockEntity) level.getBlockEntity(firePos);
        var blast = (PortBlastFurnaceBlockEntity) level.getBlockEntity(blastPos);
        AtomicInteger fireSounds = new AtomicInteger(), blastSounds = new AtomicInteger();
        Consumer<PlayLevelSoundEvent.AtPosition> capture = event -> {
            if (event.getLevel() != level || event.getSound().value() != SoundEvents.FIRE_AMBIENT) return;
            var position = event.getPosition();
            boolean isFire = position.equals(new Vec3(firePos.getX(), firePos.getY(), firePos.getZ()));
            boolean isBlast = position.equals(new Vec3(blastPos.getX(), blastPos.getY(), blastPos.getZ()));
            if (!isFire && !isBlast) return;
            if (event.getSource() != SoundSource.BLOCKS || event.getOriginalVolume() != 1.0F
                    || event.getOriginalPitch() < 0.5F || event.getOriginalPitch() >= (isFire ? 1.0F : 0.75F)) {
                throw new IllegalStateException("Source fire crackle pitch, volume or category changed");
            }
            (isFire ? fireSounds : blastSounds).incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(capture);
        try {
            for (int tick = 0; tick < 100; tick++) {
                PortFireboxBlockEntity.tick(level, firePos, fireState, firebox);
                PortBlastFurnaceBlockEntity.tick(level, blastPos, blastState, blast);
            }
            if (fireSounds.get() != 0 || blastSounds.get() != 0) throw new IllegalStateException("Idle machine crackled");
            firebox.items().setStackInSlot(0, new ItemStack(Items.COAL));
            blast.items().setStackInSlot(0, new ItemStack(Items.COAL));
            blast.items().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 2));
            blast.items().setStackInSlot(2, new ItemStack(Items.SAND));
            for (int tick = 0; tick < 500; tick++) {
                PortFireboxBlockEntity.tick(level, firePos, fireState, firebox);
                PortBlastFurnaceBlockEntity.tick(level, blastPos, blastState, blast);
            }
            if (fireSounds.get() == 0 || blastSounds.get() == 0) throw new IllegalStateException("Active machine crackle is missing");
            int before = blastSounds.get();
            blast.items().setStackInSlot(3, new ItemStack(PortItems.INGOT_STEEL.get(), 64));
            for (int tick = 0; tick < 500; tick++) PortBlastFurnaceBlockEntity.tick(level, blastPos, blastState, blast);
            if (blastSounds.get() != before) throw new IllegalStateException("Output-blocked blast furnace crackled");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(capture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourcePressSoundOccursOncePerSuccessfulStamp(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = PortBlocks.MACHINE_PRESS.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        var press = (PortPressBlockEntity) level.getBlockEntity(pos);
        var sound = PortSounds.PRESS_OPERATE.get();
        if (ForgeRegistries.SOUND_EVENTS.getValue(sound.getLocation()) != sound) {
            throw new IllegalStateException("Source press sound is not registered");
        }
        AtomicInteger played = new AtomicInteger();
        Consumer<PlayLevelSoundEvent.AtPosition> capture = event -> {
            if (event.getLevel() != level || event.getSound().value() != sound
                    || !event.getPosition().equals(new Vec3(pos.getX(), pos.getY(), pos.getZ()))) return;
            if (event.getSource() != SoundSource.BLOCKS || event.getOriginalVolume() != 1.5F
                    || event.getOriginalPitch() != 1.0F) {
                throw new IllegalStateException("Press playback differs from the source unmuffled volume/pitch/position");
            }
            played.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(capture);
        try {
            press.items().setStackInSlot(0, new ItemStack(Items.COAL));
            for (int tick = 0; tick < 100; tick++) PortPressBlockEntity.tick(level, pos, state, press);
            if (played.get() != 0) throw new IllegalStateException("Idle press played a stamping sound");
            press.items().setStackInSlot(1, new ItemStack(PortItems.STAMP_IRON_PLATE.get()));
            press.items().setStackInSlot(2, new ItemStack(Items.COPPER_INGOT, 2));
            for (int tick = 0; tick < 1400 && press.items().getStackInSlot(3).getCount() < 2; tick++) {
                PortPressBlockEntity.tick(level, pos, state, press);
            }
            if (press.items().getStackInSlot(3).getCount() != 2 || played.get() != 2
                    || press.items().getStackInSlot(1).getDamageValue() != 2) {
                throw new IllegalStateException("Press did not play exactly one source sound for each completed stamp");
            }
            press.items().setStackInSlot(3, new ItemStack(PortItems.PLATE_COPPER.get(), 64));
            press.items().setStackInSlot(2, new ItemStack(Items.COPPER_INGOT));
            for (int tick = 0; tick < 500; tick++) PortPressBlockEntity.tick(level, pos, state, press);
            if (played.get() != 2) throw new IllegalStateException("Retracting or output-blocked press replayed its sound");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(capture);
        }
        helper.succeed();
    }
}
