package com.hbm;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.IdentityHashMap;
import java.util.Map;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/** Renders the original OBJ's optional supports and installed pack groups above its static socket. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortBatterySocketRenderer implements BlockEntityRenderer<PortBatterySocketBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SUPPORTS = model("socket_supports");
    private static final RenderType LAYER = RenderType.cutout();
    private static volatile boolean modelsAudited;
    private final Map<Item, ResourceLocation> packModels = new IdentityHashMap<>();

    public PortBatterySocketRenderer(BlockEntityRendererProvider.Context context) {
        for (var pack : PortItems.BATTERY_PACKS) {
            packModels.put(pack.get(), model("socket_" + pack.getId().getPath()));
        }
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(SUPPORTS);
        for (var pack : PortItems.BATTERY_PACKS) {
            event.register(model("socket_" + pack.getId().getPath()));
        }
    }

    @SubscribeEvent
    public static void auditBakedModels(ModelEvent.BakingCompleted event) {
        if (!Boolean.getBoolean("hbm.port.auditSocketModels")) return;
        modelsAudited = false;
        validateModel(event, SUPPORTS, new ResourceLocation(HbmPort.MOD_ID, "block/battery_socket"));
        for (var pack : PortItems.BATTERY_PACKS) {
            String id = pack.getId().getPath();
            String sourceName = id.substring("battery_pack_".length());
            if (!sourceName.startsWith("capacitor_")) sourceName = "battery_" + sourceName;
            validateModel(event, model("socket_" + id),
                    new ResourceLocation(HbmPort.MOD_ID, "models/machines/" + sourceName));
        }
        modelsAudited = true;
        LOGGER.info("Validated the socket supports and {} installed-pack OBJ layers", PortItems.BATTERY_PACKS.size());
    }

    static boolean modelsAudited() {
        return modelsAudited;
    }

    private static void validateModel(ModelEvent.BakingCompleted event, ResourceLocation id,
            ResourceLocation expectedSprite) {
        BakedModel model = event.getModels().get(id);
        if (model == null || model == event.getModelManager().getMissingModel()) {
            throw new IllegalStateException("Socket OBJ layer did not bake: " + id);
        }
        int quads = 0;
        var state = PortBlocks.MACHINE_BATTERY_SOCKET.get().defaultBlockState();
        RandomSource random = RandomSource.create(0L);
        for (Direction face : new Direction[] {null, Direction.DOWN, Direction.UP,
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (var quad : model.getQuads(state, face, random, ModelData.EMPTY, LAYER)) {
                if (!quad.getSprite().contents().name().equals(expectedSprite)) {
                    throw new IllegalStateException("Socket OBJ layer " + id + " used sprite "
                            + quad.getSprite().contents().name() + " instead of " + expectedSprite);
                }
                quads++;
            }
        }
        if (quads == 0) throw new IllegalStateException("Socket OBJ layer has no geometry: " + id);
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PortBlockEntities.BATTERY_SOCKET.get(), PortBatterySocketRenderer::new);
    }

    @Override
    public void render(PortBatterySocketBlockEntity socket, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = socket.getLevel();
        if (level == null) return;
        ItemStack stack = socket.items().getStackInSlot(0);
        ResourceLocation packModel = packModels.get(stack.getItem());
        boolean showSupports = !level.getBlockState(socket.getBlockPos().above(2)).isAir();
        if (packModel == null && !showSupports) return;

        poseStack.pushPose();
        Direction facing = socket.getBlockState().getValue(PortBatterySocketBlock.FACING);
        float degrees = switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(degrees)));
        if (showSupports) {
            renderModel(socket, SUPPORTS, poseStack, buffers, packedLight, packedOverlay);
        }
        if (packModel != null) renderModel(socket, packModel, poseStack, buffers, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderModel(PortBatterySocketBlockEntity socket, ResourceLocation id,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(id);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        renderer.renderModel(poseStack.last(), buffers.getBuffer(LAYER), socket.getBlockState(), model,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay, ModelData.EMPTY, LAYER);
    }

    private static ResourceLocation model(String path) {
        return new ResourceLocation(HbmPort.MOD_ID, "block/" + path);
    }
}
