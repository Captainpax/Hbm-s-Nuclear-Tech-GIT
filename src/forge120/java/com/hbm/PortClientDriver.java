package com.hbm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in local QA driver. Uses the ordinary client interaction APIs and server packets. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class PortClientDriver {
    private static final String SESSION = System.getProperty("hbm.port.clientDriver", "");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static long lastId = -1;
    private static int ticks;
    private static JsonObject pending;
    private static int settleUntil;
    private static String error;
    private static JsonObject completed;
    private static boolean joinedFixture;

    private PortClientDriver() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (SESSION.isBlank() || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        ticks++;
        try {
            if (ticks % 20 == 0) write(client, "hbm-driver-state.json", state(client));
            // A completed action is never replayed when an output file is locked.
            if (completed != null) {
                write(client, "hbm-driver-result.json", completed);
                completed = null;
            }
            if (pending != null) {
                if (ticks >= settleUntil) {
                    completed = state(client);
                    completed.addProperty("id", lastId);
                    completed.addProperty("action", pending.get("action").getAsString());
                    completed.addProperty("ok", error == null);
                    if (error != null) completed.addProperty("error", error);
                    pending = null;
                }
                return;
            }
            if (ticks % 5 != 0) return;
            Path inbox = client.gameDirectory.toPath().resolve("hbm-driver-command.json");
            if (!Files.isRegularFile(inbox)) return;
            JsonObject request = JsonParser.parseString(Files.readString(inbox)).getAsJsonObject();
            if (!SESSION.equals(request.get("session").getAsString())) return;
            String action = request.get("action").getAsString();
            if ((client.player == null || client.level == null)
                    && !(joinedFixture && (action.equals("reconnect") || action.equals("exit")))) return;
            long id = request.get("id").getAsLong();
            if (id <= lastId || client.level != null && request.has("notBeforeTick")
                    && client.level.getGameTime() < request.get("notBeforeTick").getAsLong()) return;
            lastId = id;
            pending = request;
            error = null;
            settleUntil = ticks + 20;
            try { act(client, request); }
            catch (Exception failure) { error = failure.getMessage(); }
        } catch (Exception failure) {
            // Runtime evidence belongs to the ignored run directory; a failed QA
            // request must not crash the client or become a production feature.
            if (ticks % 100 == 0) LogUtils.getLogger().warn("Local HBM client driver: {}", failure.toString());
        }
    }

    private static boolean inFixture(BlockPos pos) {
        return pos.getX() >= 24 && pos.getX() <= 43 && pos.getY() >= 98 && pos.getY() <= 112
                && pos.getZ() >= 0 && pos.getZ() <= 30;
    }

    private static void act(Minecraft client, JsonObject request) throws java.io.IOException {
        String action = request.get("action").getAsString();
        if (action.equals("reconnect")) {
            if (!joinedFixture || client.player != null || client.level != null) {
                throw new IllegalStateException("Reconnect requires a previously joined fixture and a disconnected client");
            }
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString("127.0.0.1:25566"),
                    new ServerData("HBM local QA", "127.0.0.1:25566", false), false);
            return;
        }
        if (action.equals("exit") && joinedFixture && client.player == null && client.level == null) {
            exit(client, request);
            return;
        }
        if (client.getCurrentServer() == null || !"127.0.0.1:25566".equals(client.getCurrentServer().ip)
                || client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL
                || !java.util.Set.of("Dev", "HbmObserver").contains(client.player.getGameProfile().getName())
                || !inFixture(client.player.blockPosition())) {
            throw new IllegalStateException("Client driver requires the named Survival clients inside the loopback fixture");
        }
        joinedFixture = true;
        switch (action) {
            case "inspect" -> { }
            case "open" -> {
                BlockPos pos = new BlockPos(request.get("x").getAsInt(), request.get("y").getAsInt(), request.get("z").getAsInt());
                if (!inFixture(pos) || client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 20.25) {
                    throw new IllegalArgumentException("Target must be a reachable fixture block");
                }
                if (client.player.containerMenu != client.player.inventoryMenu) throw new IllegalStateException("Close the current menu first");
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            }
            case "click" -> {
                var menu = client.player.containerMenu;
                int slot = request.get("slot").getAsInt(), button = request.get("button").getAsInt();
                ClickType type = ClickType.valueOf(request.get("mode").getAsString());
                if (menu == client.player.inventoryMenu || slot < 0 || slot >= menu.slots.size()
                        || button < 0 || button > 1 || type != ClickType.PICKUP && type != ClickType.QUICK_MOVE) {
                    throw new IllegalArgumentException("Only normal in-menu pickup or shift-click actions are supported");
                }
                client.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, client.player);
            }
            case "button" -> {
                var menu = client.player.containerMenu;
                int button = request.get("button").getAsInt();
                if (!(menu instanceof PortCrucibleMenu) || button < 0 || button > 1) {
                    throw new IllegalArgumentException("Only the fixture crucible's None/Steel selector is supported");
                }
                client.gameMode.handleInventoryButtonClick(menu.containerId, button);
            }
            case "close" -> client.player.closeContainer();
            case "exit" -> {
                if (!client.player.containerMenu.getCarried().isEmpty()) throw new IllegalStateException("Clear the cursor before exiting");
                exit(client, request);
            }
            default -> throw new IllegalArgumentException("Unknown local QA action");
        }
    }

    private static void exit(Minecraft client, JsonObject request) throws java.io.IOException {
        JsonObject receipt = state(client);
        receipt.addProperty("id", lastId);
        receipt.addProperty("action", "exit"); receipt.addProperty("ok", true);
        receipt.addProperty("exitRequested", true);
        write(client, "hbm-driver-result.json", receipt);
        pending = null;
        client.stop();
    }

    private static JsonObject state(Minecraft client) {
        JsonObject result = new JsonObject();
        result.addProperty("session", SESSION);
        result.addProperty("timestamp", System.currentTimeMillis());
        result.addProperty("connected", client.player != null && client.level != null);
        if (client.player == null || client.level == null) return result;
        result.addProperty("player", client.player.getGameProfile().getName());
        result.addProperty("uuid", client.player.getUUID().toString());
        result.addProperty("gameTime", client.level.getGameTime());
        result.addProperty("gameMode", client.gameMode.getPlayerMode().getName());
        result.addProperty("x", client.player.getX()); result.addProperty("y", client.player.getY()); result.addProperty("z", client.player.getZ());
        var menu = client.player.containerMenu;
        result.addProperty("menu", menu.getClass().getSimpleName());
        result.addProperty("containerId", menu.containerId);
        result.addProperty("stateId", menu.getStateId());
        result.add("carried", item(menu.getCarried()));
        JsonArray slots = new JsonArray();
        for (int i = 0; i < menu.slots.size(); i++) {
            JsonObject slot = item(menu.getSlot(i).getItem()); slot.addProperty("slot", i); slots.add(slot);
        }
        result.add("slots", slots);
        JsonArray inventory = new JsonArray();
        for (int i = 0; i < client.player.getInventory().items.size(); i++) {
            JsonObject slot = item(client.player.getInventory().getItem(i)); slot.addProperty("slot", i); inventory.add(slot);
        }
        result.add("inventory", inventory);
        if (menu instanceof PortCrucibleMenu crucible) {
            result.addProperty("selectedRecipe", crucible.selectedIndex());
            result.addProperty("heat", crucible.heat()); result.addProperty("progress", crucible.progress());
            JsonObject recipe = new JsonObject(), waste = new JsonObject();
            for (PortMaterial material : PortMaterial.values()) {
                recipe.addProperty(material.name(), crucible.amount(true, material));
                waste.addProperty(material.name(), crucible.amount(false, material));
            }
            result.add("recipeMaterials", recipe); result.add("wasteMaterials", waste);
        }
        return result;
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        if (!stack.isEmpty() && stack.hasTag()) result.addProperty("tag", stack.getTag().toString());
        return result;
    }

    private static void write(Minecraft client, String name, JsonObject contents) throws java.io.IOException {
        Path target = client.gameDirectory.toPath().resolve(name);
        Path temporary = target.resolveSibling(name + ".tmp");
        Files.writeString(temporary, JSON.toJson(contents));
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
