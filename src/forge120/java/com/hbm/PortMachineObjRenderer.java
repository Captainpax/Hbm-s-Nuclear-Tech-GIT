package com.hbm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.math.Axis;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/** Source machine OBJs with core-only multiblock rendering and a moving press ram. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortMachineObjRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RenderType LAYER = RenderType.cutout();
    private static final ResourceLocation FIREBOX_EMPTY = model("firebox_obj_empty");
    private static final ResourceLocation FIREBOX_BURNING = model("firebox_obj_burning");
    private static final ResourceLocation CRUCIBLE = model("crucible_obj");
    private static final ResourceLocation CRUCIBLE_LAVA = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
            "textures/models/machines/lava.png");
    private static final ResourceLocation BLAST = model("blast_furnace_obj");
    private static final ResourceLocation PRESS_BODY = model("press_body_obj");
    private static final ResourceLocation PRESS_HEAD = model("press_head_obj");
    private static final ResourceLocation ANVIL_IRON = model("anvil_iron");
    private static final ResourceLocation ANVIL_STEEL = model("anvil_steel");
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
        event.register(PRESS_BODY);
        event.register(PRESS_HEAD);
        event.register(ANVIL_IRON);
        event.register(ANVIL_STEEL);
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
        if (Minecraft.getInstance().getResourceManager().getResource(CRUCIBLE_LAVA).isEmpty()) {
            throw new IllegalStateException("Crucible molten-surface texture is missing");
        }
        validate(event, BLAST, "blast_furnace");
        validate(event, PRESS_BODY, "press_body");
        validate(event, PRESS_HEAD, "press_head");
        validate(event, ANVIL_IRON, "block/anvil_iron");
        validate(event, ANVIL_STEEL, "block/anvil_steel");
        for (var block : new PortAnvilBlock[] {(PortAnvilBlock) PortBlocks.ANVIL_IRON.get(),
                (PortAnvilBlock) PortBlocks.ANVIL_STEEL.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                validateAnvilState(event, block.defaultBlockState().setValue(PortAnvilBlock.FACING, facing));
            }
        }
        modelsAudited = true;
        LOGGER.info("Validated casting, blast-furnace, press and anvil OBJ geometry and textures");
    }

    public static boolean modelsAudited() { return modelsAudited; }

    private static void validateAnvilState(ModelEvent.BakingCompleted event, BlockState state) {
        var id = BlockModelShaper.stateToModelLocation(state);
        BakedModel baked = event.getModels().get(id);
        if (baked == null || baked == event.getModelManager().getMissingModel()
                || !baked.getRenderTypes(state, RandomSource.create(0), ModelData.EMPTY).contains(LAYER)) {
            throw new IllegalStateException("Anvil blockstate did not bake with its cutout render layer: " + id);
        }
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Direction face : new Direction[] {null, Direction.DOWN, Direction.UP,
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (var quad : baked.getQuads(state, face, RandomSource.create(0), ModelData.EMPTY, LAYER)) {
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float x = Float.intBitsToFloat(data[vertex * stride]);
                    float y = Float.intBitsToFloat(data[vertex * stride + 1]);
                    float z = Float.intBitsToFloat(data[vertex * stride + 2]);
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (Math.abs(minY) > 0.001F || Math.abs(maxY - 0.6885F) > 0.001F
                || minX < -0.5F || maxX > 1.5F || minZ < -0.5F || maxZ > 1.5F) {
            throw new IllegalStateException("Anvil blockstate geometry is displaced: " + id
                    + " bounds " + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);
        }
    }

    private static void validate(ModelEvent.BakingCompleted event, ResourceLocation id, String texture) {
        BakedModel baked = event.getModels().get(id);
        if (baked == null || baked == event.getModelManager().getMissingModel()) {
            throw new IllegalStateException("Machine OBJ did not bake: " + id);
        }
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID,
                texture.startsWith("block/") ? texture : "models/machines/" + texture);
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
        event.registerBlockEntityRenderer(PortBlockEntities.PRESS.get(),
                context -> new PortMachineObjRenderer<>(context, PRESS_BODY));
    }

    @Override
    public boolean shouldRenderOffScreen(T entity) { return true; }

    @Override
    public void render(T entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        if (entity instanceof PortPressBlockEntity press) {
            renderPress(press, partialTick, poseStack, buffers, packedLight, packedOverlay);
            return;
        }
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
        renderModel(entity, id, poseStack, buffers, packedLight, packedOverlay);
        if (entity instanceof PortCrucibleBlockEntity crucible && crucible.renderMass() > 0) {
            float height = 0.5F + crucible.renderMass() * 0.875F / (PortCrucibleBlockEntity.STORE_CAPACITY * 2);
            var pose = poseStack.last();
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(CRUCIBLE_LAVA));
            moltenVertex(pose, vertices, -1, height, -1, 0, 0, packedOverlay);
            moltenVertex(pose, vertices, -1, height, 1, 0, 1, packedOverlay);
            moltenVertex(pose, vertices, 1, height, 1, 1, 1, packedOverlay);
            moltenVertex(pose, vertices, 1, height, -1, 1, 0, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void moltenVertex(PoseStack.Pose pose, VertexConsumer vertices,
            float x, float y, float z, float u, float v, int overlay) {
        vertices.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(overlay).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
    }

    private static void renderPress(PortPressBlockEntity press, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180));
        renderModel(press, PRESS_BODY, pose, buffers, light, overlay);
        pose.popPose();
        pose.pushPose();
        pose.scale(0.99F, 1, 0.99F);
        pose.translate(0, (1.0 - press.renderPress(partialTick) / PortPressBlockEntity.MAX_PRESS) * 0.875, 0);
        renderModel(press, PRESS_HEAD, pose, buffers, light, overlay);
        pose.popPose();
        if (!press.displayInput().isEmpty()) {
            pose.pushPose();
            pose.translate(0, 1.006, -0.103125);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.5F, 0.5F, 0.5F);
            Minecraft.getInstance().getItemRenderer().renderStatic(press.displayInput(), ItemDisplayContext.FIXED,
                    light, overlay, pose, buffers, press.getLevel(), 0);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void renderModel(BlockEntity entity, ResourceLocation id, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel baked = minecraft.getModelManager().getModel(id);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), buffers.getBuffer(LAYER), entity.getBlockState(), baked,
                1, 1, 1, light, overlay, ModelData.EMPTY, LAYER);
    }

    private static ResourceLocation model(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "block/" + path);
    }
}
