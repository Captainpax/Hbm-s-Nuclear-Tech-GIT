package com.hbm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/** Core-only rendering of the original three-wide firebox, crucible and blast-furnace OBJs. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortMachineObjRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RenderType LAYER = RenderType.cutout();
    private static final ResourceLocation FIREBOX_EMPTY = model("firebox_obj_empty");
    private static final ResourceLocation FIREBOX_BURNING = model("firebox_obj_burning");
    private static final ResourceLocation CRUCIBLE = model("crucible_obj");
    private static final ResourceLocation BLAST = model("blast_furnace_obj");
    private static volatile boolean modelsAudited;
    private final ResourceLocation defaultModel;

    private PortMachineObjRenderer(BlockEntityRendererProvider.Context context, ResourceLocation defaultModel) {
        this.defaultModel = defaultModel;
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(FIREBOX_EMPTY);
        event.register(FIREBOX_BURNING);
        event.register(CRUCIBLE);
        event.register(BLAST);
    }

    @SubscribeEvent
    public static void auditBakedModels(ModelEvent.BakingCompleted event) {
        // Strict source-asset assertions are a development/CI gate. Resource
        // packs may legitimately replace models and sprites in an installed game.
        if (!Boolean.getBoolean("hbm.port.auditSocketModels")
                && !Boolean.getBoolean("hbm.port.clientSmoke")) return;
        modelsAudited = false;
        validate(event, FIREBOX_EMPTY, "firebox");
        validate(event, FIREBOX_BURNING, "firebox");
        validate(event, CRUCIBLE, "crucible_heat");
        validate(event, BLAST, "blast_furnace");
        modelsAudited = true;
        LOGGER.info("Validated firebox, crucible and blast-furnace OBJ geometry and textures");
    }

    public static boolean modelsAudited() { return modelsAudited; }

    private static void validate(ModelEvent.BakingCompleted event, ResourceLocation id, String texture) {
        BakedModel baked = event.getModels().get(id);
        if (baked == null || baked == event.getModelManager().getMissingModel()) {
            throw new IllegalStateException("Machine OBJ did not bake: " + id);
        }
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
                "models/machines/" + texture);
        int quads = 0;
        RandomSource random = RandomSource.create(0L);
        for (Direction face : new Direction[] {null, Direction.DOWN, Direction.UP,
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (var quad : baked.getQuads(null, face, random, ModelData.EMPTY, LAYER)) {
                if (!quad.getSprite().contents().name().equals(expected)) {
                    throw new IllegalStateException("Machine OBJ " + id + " used "
                            + quad.getSprite().contents().name() + " instead of " + expected);
                }
                quads++;
            }
        }
        if (quads == 0) throw new IllegalStateException("Machine OBJ has no geometry: " + id);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PortBlockEntities.FIREBOX.get(),
                context -> new PortMachineObjRenderer<>(context, FIREBOX_EMPTY));
        event.registerBlockEntityRenderer(PortBlockEntities.CRUCIBLE.get(),
                context -> new PortMachineObjRenderer<>(context, CRUCIBLE));
        event.registerBlockEntityRenderer(PortBlockEntities.BLAST_FURNACE.get(),
                context -> new PortMachineObjRenderer<>(context, BLAST));
    }

    @Override
    public boolean shouldRenderOffScreen(T entity) { return true; }

    @Override
    public void render(T entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        ResourceLocation id = defaultModel;
        if (entity instanceof PortFireboxBlockEntity firebox && firebox.burnTime() > 0) {
            id = FIREBOX_BURNING;
        }
        BlockState state = entity.getBlockState();
        Direction facing = state.hasProperty(PortCastingMachineBlock.FACING)
                ? state.getValue(PortCastingMachineBlock.FACING) : Direction.EAST;
        float degrees = switch (facing) {
            case NORTH -> 90.0F;
            case SOUTH -> 270.0F;
            case WEST -> 180.0F;
            default -> 0.0F;
        };
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(degrees)));
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(id);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(poseStack.last(), buffers.getBuffer(LAYER), state, model,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay, ModelData.EMPTY, LAYER);
        poseStack.popPose();
    }

    private static ResourceLocation model(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "block/" + path);
    }
}
