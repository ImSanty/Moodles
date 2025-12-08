package net.imsanty.moodles.moodle.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public final class PlayerMoodlePersistentState extends PersistentState {
  private static final String STATE_NAME = "moodles_player_data";
  private static final String PLAYERS_KEY = "players";

  private static final Type<PlayerMoodlePersistentState> TYPE = new Type<>(
      PlayerMoodlePersistentState::new,
      PlayerMoodlePersistentState::fromNbt,
      null);

  private final Map<UUID, NbtCompound> entries = new HashMap<>();

  public static PlayerMoodlePersistentState get(MinecraftServer server) {
    PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
    return manager.getOrCreate(TYPE, STATE_NAME);
  }

  private static PlayerMoodlePersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    PlayerMoodlePersistentState state = new PlayerMoodlePersistentState();
    if (nbt == null) {
      return state;
    }
    NbtCompound players = nbt.getCompound(PLAYERS_KEY);
    for (String key : players.getKeys()) {
      try {
        UUID id = UUID.fromString(key);
        state.entries.put(id, players.getCompound(key).copy());
      } catch (IllegalArgumentException ignored) {
      }
    }
    return state;
  }

  @Override
  public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    NbtCompound players = new NbtCompound();
    for (Map.Entry<UUID, NbtCompound> entry : entries.entrySet()) {
      players.put(entry.getKey().toString(), entry.getValue().copy());
    }
    nbt.put(PLAYERS_KEY, players);
    return nbt;
  }

  public NbtCompound get(UUID uuid) {
    NbtCompound compound = entries.get(uuid);
    return compound == null ? null : compound.copy();
  }

  public void put(UUID uuid, NbtCompound data) {
    if (data == null) {
      entries.remove(uuid);
    } else {
      entries.put(uuid, data.copy());
    }
    markDirty();
  }

  public void remove(UUID uuid) {
    if (entries.remove(uuid) != null) {
      markDirty();
    }
  }
}
