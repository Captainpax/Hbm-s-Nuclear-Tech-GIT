package com.hbm;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Source Geiger and player cough sound events. */
public final class PortSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HbmPort.MOD_ID);

    // The old ID item.techBoop is not a valid modern ResourceLocation (uppercase B).
    public static final RegistryObject<SoundEvent> TECH_BOOP = register("item.tech_boop");
    public static final RegistryObject<SoundEvent> COUGH = register("player.cough");
    private static final List<RegistryObject<SoundEvent>> GEIGER = List.of(
            register("item.geiger1"), register("item.geiger2"), register("item.geiger3"),
            register("item.geiger4"), register("item.geiger5"), register("item.geiger6"));

    private PortSounds() {}

    private static RegistryObject<SoundEvent> register(String path) {
        ResourceLocation id = new ResourceLocation(HbmPort.MOD_ID, path);
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    static SoundEvent geiger(int number) {
        if (number < 1 || number > GEIGER.size()) {
            throw new IllegalArgumentException("Geiger sound number must be 1–6");
        }
        return GEIGER.get(number - 1).get();
    }
}
