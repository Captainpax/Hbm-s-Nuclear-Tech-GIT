package com.hbm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** Source small-mold item heights and full-bright, copper-tinted molten surface. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortFoundryMoldRenderer implements BlockEntityRenderer<PortFoundryMoldBlockEntity> {
    private static final ResourceLocation LAVA = ResourceLocation.fromNamespaceAndPath(
            HbmPort.MOD_ID, "textures/models/machines/lava_gray.png");
    private static boolean modelsAudited;
    private final ItemStack moldItem = new ItemStack(PortItems.MOLD_PLATE_CAST.get());
    private final ItemStack castItem = new ItemStack(PortItems.PLATE_CAST_COPPER.get());

    public PortFoundryMoldRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PortBlockEntities.FOUNDRY_MOLD.get(), PortFoundryMoldRenderer::new);
    }

    @SubscribeEvent
    public static void auditModel(ModelEvent.BakingCompleted event) {
        if (!Boolean.getBoolean("hbm.port.auditSocketModels")
                && !Boolean.getBoolean("hbm.port.clientSmoke")) return;
        modelsAudited = false;
        var state = PortBlocks.FOUNDRY_MOLD.get().defaultBlockState();
        var model = event.getModels().get(BlockModelShaper.stateToModelLocation(state));
        if (model == null || model == event.getModelManager().getMissingModel()
                || !model.getRenderTypes(state, RandomSource.create(0), ModelData.EMPTY).contains(RenderType.cutout())) {
            throw new IllegalStateException("Foundry mold did not bake with its cutout layer");
        }
        Set<String> sprites = new HashSet<>();
        int faces = 0;
        boolean floor = false, rim = false;
        for (Direction direction : new Direction[] {null, Direction.DOWN, Direction.UP,
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (var quad : model.getQuads(state, direction, RandomSource.create(0), ModelData.EMPTY, RenderType.cutout())) {
                faces++;
                String texture = quad.getSprite().contents().name().toString();
                sprites.add(texture);
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                float y = Float.intBitsToFloat(data[1]);
                floor |= quad.getDirection() == Direction.UP && Math.abs(y - 0.125F) < 0.0001F
                        && texture.equals("hbm:block/foundry_mold_bottom");
                rim |= quad.getDirection() == Direction.UP && Math.abs(y - 0.5F) < 0.0001F
                        && texture.equals("hbm:block/foundry_mold_top");
                for (int vertex = 0; vertex < 4; vertex++) {
                    float x = Float.intBitsToFloat(data[vertex * stride]);
                    y = Float.intBitsToFloat(data[vertex * stride + 1]);
                    float z = Float.intBitsToFloat(data[vertex * stride + 2]);
                    if (x < 0 || x > 1 || y < 0 || y > 0.5F || z < 0 || z > 1) {
                        throw new IllegalStateException("Foundry mold geometry is outside the source bounds");
                    }
                }
            }
        }
        if (faces != 11 || !floor || !rim || !sprites.equals(Set.of("hbm:block/foundry_mold_top",
                "hbm:block/foundry_mold_side", "hbm:block/foundry_mold_bottom", "hbm:block/foundry_mold_inner"))) {
            throw new IllegalStateException("Foundry mold is missing a source face or texture");
        }
        if (Minecraft.getInstance().getResourceManager().getResource(LAVA).isEmpty()) {
            throw new IllegalStateException("Foundry molten texture is missing");
        }
        modelsAudited = true;
        LogUtils.getLogger().info("Validated hollow foundry mold faces, source textures and molten surface resource");
    }

    public static boolean modelsAudited() { return modelsAudited; }

    @Override public void render(PortFoundryMoldBlockEntity mold, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (mold.displayMold()) drawItem(moldItem, 0.13, mold, pose, buffers, light, overlay);
        if (mold.displayCasting()) drawItem(castItem, 0.25, mold, pose, buffers, light, overlay);
        if (mold.copperQuanta() <= 0) return;
        float height = 0.125F + mold.copperQuanta() * 0.25F / PortFoundryMoldBlockEntity.CAST_PLATE_QUANTA;
        // Mats.MAT_COPPER moltenColor = C18336. The source adds a white 30% glow pass.
        surface(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(LAVA)), height,
                193, 131, 54, 255, overlay);
        // The modern eyes layer uses ONE/ONE blending; scale RGB instead of source alpha.
        surface(pose.last(), buffers.getBuffer(RenderType.eyes(LAVA)), height,
                77, 77, 77, 255, overlay);
    }

    private static void drawItem(ItemStack stack, double height, PortFoundryMoldBlockEntity mold,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, height, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        // Legacy RenderItemIntoGUI draws a flat twelve-pixel icon, without item extrusion.
        pose.scale(0.75F, 0.75F, 0.001F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, mold.getLevel(), 0);
        pose.popPose();
    }

    private static void surface(PoseStack.Pose pose, VertexConsumer vertices, float height,
            int red, int green, int blue, int alpha, int overlay) {
        vertex(pose, vertices, 0.125F, height, 0.125F, 0.125F, 0.875F, red, green, blue, alpha, overlay);
        vertex(pose, vertices, 0.125F, height, 0.875F, 0.875F, 0.875F, red, green, blue, alpha, overlay);
        vertex(pose, vertices, 0.875F, height, 0.875F, 0.875F, 0.125F, red, green, blue, alpha, overlay);
        vertex(pose, vertices, 0.875F, height, 0.125F, 0.125F, 0.125F, red, green, blue, alpha, overlay);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
            float u, float v, int red, int green, int blue, int alpha, int overlay) {
        vertices.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha).uv(u, v)
                .overlayCoords(overlay).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
