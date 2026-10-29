package com.hbm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in client-only Survival walkthrough. All changes use normal client gameplay APIs. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class PortSurvivalClientDriver {
    private static final String SESSION = System.getProperty("hbm.port.survivalDriver", "");
    private static final String WORLD = "HBM Survival Progression";
    private static long lastId = -1;
    private static int ticks, finishAt, deadline;
    private static JsonObject pending, before, completed;
    private static String error, running;
    private static BlockPos target;
    private static BlockState mining;
    private static Vec3 destination;
    private static boolean firstState, bootstrapped;

    private PortSurvivalClientDriver() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (SESSION.isBlank() || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        ticks++;
        try {
            // A command left by a previous client process is never replayed.
            if (!bootstrapped) {
                var inbox = client.gameDirectory.toPath().resolve("hbm-survival-command.json");
                if (Files.isRegularFile(inbox)) {
                    var previous = JsonParser.parseString(Files.readString(inbox)).getAsJsonObject();
                    if (SESSION.equals(previous.get("session").getAsString())) lastId = previous.get("id").getAsLong();
                }
                bootstrapped = true;
            }
            if (ticks % 20 == 0) write(client, "state", state(client));
            if (completed != null) { write(client, "result", completed); completed = null; }
            if (client.player == null || client.level == null) { release(client); return; }
            if (pending != null) {
                if (running != null) {
                    try {
                        guard(client);
                        if (ticks >= deadline) throw new IllegalStateException("Action timed out; inspect before retrying");
                        advance(client);
                    } catch (Exception failure) {
                        error = failure.toString(); running = null; release(client); finishAt = ticks + 20;
                    }
                } else if (ticks >= finishAt) {
                    completed = state(client);
                    completed.addProperty("id", lastId); completed.add("request", pending); completed.add("before", before);
                    completed.addProperty("ok", error == null);
                    if (error != null) completed.addProperty("error", error);
                    pending = null;
                }
                return;
            }
            if (ticks % 5 != 0) return;
            var inbox = client.gameDirectory.toPath().resolve("hbm-survival-command.json");
            if (!Files.isRegularFile(inbox)) return;
            var request = JsonParser.parseString(Files.readString(inbox)).getAsJsonObject();
            if (!SESSION.equals(request.get("session").getAsString()) || request.get("id").getAsLong() <= lastId) return;
            lastId = request.get("id").getAsLong(); pending = request; before = state(client); error = null;
            finishAt = ticks + 20;
            try {
                guard(client);
                if (!firstState) {
                    if (!Files.exists(client.gameDirectory.toPath().resolve("hbm-survival-initial.json"))) write(client, "initial", before);
                    firstState = true;
                }
                act(client, request);
            } catch (Exception failure) { error = failure.toString(); release(client); }
        } catch (Exception failure) {
            if (ticks % 100 == 0) LogUtils.getLogger().warn("Survival client QA driver: {}", failure.toString());
        }
    }

    private static void guard(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server == null || !WORLD.equals(server.getWorldData().getLevelName()) || server.overworld().getSeed() != 20_301_017L
                || server.getWorldData().getAllowCommands()
                || client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL
                || !client.player.getGameProfile().getName().equals("HbmSurvival")
                || client.player.getAbilities().instabuild || client.player.getAbilities().mayfly
                || client.player.getAbilities().flying || client.player.getAbilities().invulnerable) {
            throw new IllegalStateException("Requires the named isolated single-player Survival world with cheats and flight disabled");
        }
    }

    private static void act(Minecraft client, JsonObject request) throws java.io.IOException {
        String action = request.get("action").getAsString();
        switch (action) {
            case "inspect" -> { }
            case "inventory" -> client.setScreen(new InventoryScreen(client.player));
            case "close" -> { client.player.closeContainer(); client.setScreen(null); }
            case "select" -> {
                int slot = request.get("slot").getAsInt();
                if (slot < 0 || slot > 8) throw new IllegalArgumentException("Select a hotbar slot 0-8");
                client.player.getInventory().selected = slot;
            }
            case "click" -> {
                var menu = client.player.containerMenu;
                int slot = request.get("slot").getAsInt(), button = request.has("button") ? request.get("button").getAsInt() : 0;
                var mode = ClickType.valueOf(request.has("mode") ? request.get("mode").getAsString() : "PICKUP");
                if (slot < 0 || slot >= menu.slots.size() || button < 0 || button > 1
                        || mode != ClickType.PICKUP && mode != ClickType.QUICK_MOVE) throw new IllegalArgumentException("Unsupported menu action");
                client.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, mode, client.player);
            }
            case "recipe" -> {
                var recipe = client.level.getRecipeManager().byKey(ResourceLocation.parse(request.get("recipe").getAsString())).orElseThrow();
                if (!client.player.getRecipeBook().contains(recipe)) throw new IllegalStateException("Recipe has not been unlocked by this player");
                client.gameMode.handlePlaceRecipe(client.player.containerMenu.containerId, recipe, request.has("all") && request.get("all").getAsBoolean());
            }
            case "button" -> {
                int button = request.get("button").getAsInt();
                var menu = client.player.containerMenu;
                if (!(menu instanceof PortAnvilMenu && button >= 0 && button < 10
                        || menu instanceof PortCrucibleMenu && button >= 0 && button < 2)) throw new IllegalArgumentException("Unsupported source machine button");
                client.gameMode.handleInventoryButtonClick(menu.containerId, button);
            }
            case "scan" -> { /* Read-only loaded terrain is included in the receipt below. */ }
            case "use", "mine" -> {
                playing(client);
                target = position(request);
                Vec3 aim = Vec3.atCenterOf(target);
                if (request.has("face")) aim = aim.add(Vec3.atLowerCornerOf(net.minecraft.core.Direction.valueOf(request.get("face").getAsString()).getNormal()).scale(0.49));
                look(client, aim);
                BlockHitResult hit = hit(client, target);
                if (action.equals("use")) client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                else {
                    mining = client.level.getBlockState(target);
                    if (mining.isAir() || mining.getDestroySpeed(client.level, target) < 0) throw new IllegalArgumentException("Cannot mine this block");
                    running = "mine"; deadline = ticks + 1_200;
                    client.gameMode.startDestroyBlock(target, hit.getDirection());
                    client.options.keyAttack.setDown(true);
                }
            }
            case "walk" -> {
                playing(client);
                destination = new Vec3(request.get("x").getAsDouble(), request.get("y").getAsDouble(), request.get("z").getAsDouble());
                if (client.player.position().distanceToSqr(destination) > 1_024) throw new IllegalArgumentException("Walk at most 32 blocks per action");
                running = "walk"; deadline = ticks + 1_200;
            }
            case "exit" -> {
                if (!client.player.containerMenu.getCarried().isEmpty()) throw new IllegalStateException("Clear the cursor before exit");
                var receipt = state(client); receipt.addProperty("id", lastId); receipt.addProperty("ok", true);
                receipt.addProperty("exitRequested", true); receipt.add("before", before); receipt.add("request", request);
                write(client, "result", receipt); pending = null; release(client); client.stop();
            }
            default -> throw new IllegalArgumentException("Unknown Survival QA action");
        }
    }

    private static void advance(Minecraft client) {
        playing(client);
        if (running.equals("mine")) {
            if (!client.level.getBlockState(target).equals(mining)) { finish(client); return; }
            look(client, Vec3.atCenterOf(target));
            hit(client, target);
            // Minecraft's ordinary held-attack handler advances damage once per
            // client tick. Do not call continueDestroyBlock a second time here.
            client.options.keyAttack.setDown(true);
        } else {
            Vec3 here = client.player.position();
            if (Math.hypot(destination.x - here.x, destination.z - here.z) < 0.35 && Math.abs(destination.y - here.y) < 1.3) {
                finish(client); return;
            }
            if (client.player.isInLava() || client.player.isUnderWater()) throw new IllegalStateException("Walk stopped in hazardous liquid");
            Vec3 direction = destination.subtract(here);
            look(client, client.player.getEyePosition().add(direction.x, 0, direction.z));
            client.options.keyUp.setDown(true);
            client.options.keyJump.setDown(client.player.horizontalCollision || destination.y > here.y + 0.6);
        }
    }

    private static void playing(Minecraft client) {
        if (client.screen != null || client.isPaused() || !client.player.isAlive()) throw new IllegalStateException("Requires live gameplay with menus closed");
    }
    private static void finish(Minecraft client) { release(client); running = null; finishAt = ticks + 20; }
    private static void release(Minecraft client) {
        client.options.keyUp.setDown(false); client.options.keyJump.setDown(false);
        client.options.keyAttack.setDown(false);
        if (client.gameMode != null && client.player != null) client.gameMode.stopDestroyBlock();
    }
    private static BlockPos position(JsonObject request) {
        return new BlockPos(request.get("x").getAsInt(), request.get("y").getAsInt(), request.get("z").getAsInt());
    }
    private static void look(Minecraft client, Vec3 target) {
        Vec3 delta = target.subtract(client.player.getEyePosition());
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }
    private static BlockHitResult hit(Minecraft client, BlockPos pos) {
        if (!client.level.hasChunkAt(pos) || client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 20.25
                || !(client.player.pick(4.5, 1, false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            throw new IllegalArgumentException("Target is not in reach and direct line of sight");
        }
        return hit;
    }

    private static JsonObject state(Minecraft client) {
        JsonObject state = new JsonObject();
        state.addProperty("session", SESSION); state.addProperty("timestamp", System.currentTimeMillis());
        state.addProperty("connected", client.player != null && client.level != null);
        if (client.player == null || client.level == null) return state;
        var player = client.player;
        state.addProperty("gameTime", client.level.getGameTime()); state.addProperty("dayTime", client.level.getDayTime());
        state.addProperty("player", player.getGameProfile().getName());
        state.addProperty("x", player.getX()); state.addProperty("y", player.getY()); state.addProperty("z", player.getZ());
        state.addProperty("health", player.getHealth()); state.addProperty("food", player.getFoodData().getFoodLevel());
        state.addProperty("selected", player.getInventory().selected);
        state.addProperty("mode", client.gameMode.getPlayerMode().getName());
        state.addProperty("dimension", client.level.dimension().location().toString());
        var server = client.getSingleplayerServer();
        if (server != null) {
            state.addProperty("world", server.getWorldData().getLevelName());
            state.addProperty("cheats", server.getWorldData().getAllowCommands());
        }
        var menu = player.containerMenu;
        state.addProperty("menu", menu.getClass().getSimpleName()); state.addProperty("containerId", menu.containerId);
        state.add("carried", item(menu.getCarried()));
        JsonArray inventory = new JsonArray(), slots = new JsonArray();
        for (int i = 0; i < 36; i++) { var value = item(player.getInventory().getItem(i)); value.addProperty("slot", i); inventory.add(value); }
        for (int i = 0; i < menu.slots.size(); i++) { var value = item(menu.getSlot(i).getItem()); value.addProperty("slot", i); slots.add(value); }
        state.add("inventory", inventory); state.add("slots", slots);
        if (pending != null && pending.get("action").getAsString().equals("scan")) state.add("blocks", scan(client, pending));
        return state;
    }
    private static JsonArray scan(Minecraft client, JsonObject request) {
        String match = request.has("match") ? request.get("match").getAsString() : "_log";
        int radius = request.has("radius") ? Math.max(1, Math.min(24, request.get("radius").getAsInt())) : 8;
        var center = client.player.blockPosition();
        var found = new java.util.ArrayList<JsonObject>();
        for (var pos : BlockPos.betweenClosed(center.offset(-radius, -8, -radius), center.offset(radius, 12, radius))) {
            if (!client.level.hasChunkAt(pos)) continue;
            String key = BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString();
            if (!key.contains(match)) continue;
            JsonObject value = new JsonObject(); value.addProperty("block", key);
            value.addProperty("x", pos.getX()); value.addProperty("y", pos.getY()); value.addProperty("z", pos.getZ());
            value.addProperty("distance", pos.distSqr(center)); found.add(value);
        }
        found.sort(Comparator.comparingDouble(value -> value.get("distance").getAsDouble()));
        var result = new JsonArray(); found.stream().limit(120).forEach(result::add); return result;
    }
    private static JsonObject item(ItemStack stack) {
        JsonObject result = new JsonObject(); result.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        if (stack.hasTag()) result.addProperty("tag", stack.getTag().toString());
        return result;
    }
    private static void write(Minecraft client, String name, JsonObject data) throws java.io.IOException {
        var target = client.gameDirectory.toPath().resolve("hbm-survival-" + name + ".json");
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(data));
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
