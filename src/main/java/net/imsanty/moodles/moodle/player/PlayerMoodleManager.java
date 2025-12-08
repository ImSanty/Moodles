package net.imsanty.moodles.moodle.player;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.imsanty.moodles.item.ModItems;
import net.imsanty.moodles.moodle.Moodle;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.imsanty.moodles.moodle.debug.MoodlesDebug;
import net.imsanty.moodles.moodle.network.SyncBodyHealthPayload;
import net.imsanty.moodles.moodle.network.SyncMoodlesPayload;
import net.imsanty.moodles.moodle.pain.BodyHealthScoreboard;
import net.imsanty.moodles.moodle.pain.BodyPart;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerInventory;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class PlayerMoodleManager {
  private static final Map<UUID, PlayerMoodleTracker> TRACKERS = new ConcurrentHashMap<>();

  private PlayerMoodleManager() {
  }

  public static PlayerMoodleTracker get(ServerPlayerEntity player) {
    return TRACKERS.computeIfAbsent(player.getUuid(), uuid -> {
      PlayerMoodleTracker tracker = new PlayerMoodleTracker(uuid);
      loadSnapshot(player, tracker);
      return tracker;
    });
  }

  public static void remove(ServerPlayerEntity player) {
    PlayerMoodleTracker tracker = TRACKERS.remove(player.getUuid());
    if (tracker != null) {
      persist(player, tracker);
      tracker.onPlayerDetached();
    }
    BodyHealthScoreboard.clear(player);
  }

  public static void tick(ServerPlayerEntity player) {
    PlayerMoodleTracker tracker = get(player);
    boolean severityChanged = tracker.tick(player);
    refreshScoreboard(player, tracker, false);
    tracker.flush(player);
    if (severityChanged) {
      sync(player);
    }
  }

  public static void onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
    PlayerMoodleTracker tracker = get(player);
    boolean severityChanged = tracker.onDamage(player, source, amount);
    refreshScoreboard(player, tracker, false);
    tracker.flush(player);
    if (severityChanged) {
      sync(player);
    }
  }

  public static void onExhaustion(ServerPlayerEntity player, float amount) {
    PlayerMoodleTracker tracker = get(player);
    tracker.onExhaustion(player, amount);
    tracker.flush(player);
    persist(player, tracker);
  }

  public static void onFoodEaten(ServerPlayerEntity player, ItemStack stack, FoodComponent food) {
    PlayerMoodleTracker tracker = get(player);
    tracker.onFoodEaten(player, stack, food);
    tracker.flush(player);
    persist(player, tracker);
  }

  public static void applyBandage(ServerPlayerEntity player, BodyPart part, Hand hand) {
    PlayerMoodleTracker tracker = get(player);
    ItemStack stack = player.getStackInHand(hand);
    if (stack.isEmpty() || !stack.isOf(ModItems.BANDAGE)) {
      stack = findBandageStack(player);
      if (stack.isEmpty() || !stack.isOf(ModItems.BANDAGE)) {
        return;
      }
    }
    if (!tracker.applyBandage(player, part)) {
      return;
    }
    stack.decrement(1);
    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
        SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9f, 1.2f);
    refreshScoreboard(player, tracker, true);
    tracker.flush(player);
    sync(player);
    persist(player, tracker);
  }

  public static boolean canAlwaysEat(ServerPlayerEntity player) {
    return get(player).canAlwaysEat();
  }

  public static void sync(ServerPlayerEntity player) {
    PlayerMoodleTracker tracker = get(player);
    Map<Moodle, MoodleSeverity> snapshot = tracker.snapshot();
    Map<net.minecraft.util.Identifier, MoodleSeverity> networkPayload = new LinkedHashMap<>(snapshot.size());
    snapshot.forEach((moodle, severity) -> networkPayload.put(moodle.id(), severity));
    ServerPlayNetworking.send(player, new SyncMoodlesPayload(networkPayload));
    MoodlesDebug.updateDisplay(player, snapshot);
    refreshScoreboard(player, tracker, true);
    tracker.flush(player);
    persist(player, tracker);
  }

  public static void set(ServerPlayerEntity player, Moodle moodle, MoodleSeverity severity) {
    if (get(player).set(moodle, severity)) {
      sync(player);
    }
  }

  public static void clear(ServerPlayerEntity player, Moodle moodle) {
    if (get(player).clear(moodle)) {
      sync(player);
    }
  }

  public static void transfer(ServerPlayerEntity from, ServerPlayerEntity to) {
    handleJoin(to);
  }

  private static ItemStack findBandageStack(ServerPlayerEntity player) {
    ItemStack main = player.getMainHandStack();
    if (!main.isEmpty() && main.isOf(ModItems.BANDAGE)) {
      return main;
    }
    ItemStack off = player.getOffHandStack();
    if (!off.isEmpty() && off.isOf(ModItems.BANDAGE)) {
      return off;
    }
    PlayerInventory inventory = player.getInventory();
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getStack(i);
      if (!stack.isEmpty() && stack.isOf(ModItems.BANDAGE)) {
        return stack;
      }
    }
    return ItemStack.EMPTY;
  }

  private static void refreshScoreboard(ServerPlayerEntity player, PlayerMoodleTracker tracker, boolean force) {
    boolean updated = false;
    if (force || tracker.consumeScoreboardDirtyFlag()) {
      Map<BodyPart, Float> painSnapshot = tracker.painSnapshot();
      BodyHealthScoreboard.update(player, painSnapshot, tracker.currentNutrition());
      EnumMap<BodyPart, Float> payload = new EnumMap<>(BodyPart.class);
      payload.putAll(painSnapshot);
      ServerPlayNetworking.send(player, new SyncBodyHealthPayload(payload));
      updated = true;
    }
    if (updated) {
      persist(player, tracker);
    }
  }

  public static void handleJoin(ServerPlayerEntity player) {
    PlayerMoodleTracker tracker = get(player);
    tracker.onPlayerAttached();
    sync(player);
  }

  private static void loadSnapshot(ServerPlayerEntity player, PlayerMoodleTracker tracker) {
    MinecraftServer server = player.getServer();
    if (server == null) {
      return;
    }
    PlayerMoodlePersistentState state = PlayerMoodlePersistentState.get(server);
    if (state == null) {
      return;
    }
    net.minecraft.nbt.NbtCompound data = state.get(player.getUuid());
    if (data != null) {
      tracker.readNbt(data);
    }
  }

  private static void persist(ServerPlayerEntity player, PlayerMoodleTracker tracker) {
    MinecraftServer server = player.getServer();
    if (server == null) {
      return;
    }
    PlayerMoodlePersistentState state = PlayerMoodlePersistentState.get(server);
    state.put(player.getUuid(), tracker.writeNbt());
  }
}
