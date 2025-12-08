package net.imsanty.moodles.moodle.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.imsanty.moodles.moodle.ModMoodles;
import net.imsanty.moodles.moodle.Moodle;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.imsanty.moodles.moodle.hunger.PlayerHungerTracker;
import net.imsanty.moodles.moodle.pain.BodyPart;
import net.imsanty.moodles.moodle.pain.PlayerPainTracker;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Maintains the runtime state of moodles for a single player.
 */
public final class PlayerMoodleTracker {
  private final UUID playerId;
  private final Map<Moodle, MoodleSeverity> severities;
  private final PlayerHungerTracker hungerTracker;
  private final PlayerPainTracker painTracker;
  private boolean hungerDirty;
  private boolean scoreboardDirty;

  public PlayerMoodleTracker(UUID playerId) {
    this.playerId = Objects.requireNonNull(playerId, "playerId");
    this.severities = new LinkedHashMap<>();
    this.hungerTracker = new PlayerHungerTracker();
    this.painTracker = new PlayerPainTracker();
    this.hungerDirty = true;
    this.scoreboardDirty = true; // ensure initial scoreboard/bootstrap
    initializeSeverities();
  }

  public UUID playerId() {
    return playerId;
  }

  public Map<Moodle, MoodleSeverity> snapshot() {
    return Collections.unmodifiableMap(severities);
  }

  public MoodleSeverity get(Moodle moodle) {
    return severities.getOrDefault(moodle, MoodleSeverity.NONE);
  }

  public boolean set(Moodle moodle, MoodleSeverity severity) {
    MoodleSeverity current = get(moodle);
    if (current == severity) {
      return false;
    }
    severities.put(moodle, severity);
    return true;
  }

  public boolean clear(Moodle moodle) {
    return set(moodle, MoodleSeverity.NONE);
  }

  public boolean onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
    boolean changed = painTracker.onDamage(player, source, amount);
    boolean severityChanged = update(ModMoodles.PAIN, painTracker.currentSeverity());
    scoreboardDirty |= (changed || severityChanged);
    return severityChanged;
  }

  public void onExhaustion(ServerPlayerEntity player, float amount) {
    hungerTracker.onExhaustion(player, amount);
    hungerDirty = true;
    scoreboardDirty = true;
  }

  public void onFoodEaten(ServerPlayerEntity player, ItemStack stack, FoodComponent food) {
    hungerTracker.onEat(stack, food);
    hungerDirty = true;
    scoreboardDirty = true;
  }

  public boolean applyBandage(ServerPlayerEntity player, BodyPart part) {
    boolean healed = painTracker.applyBandage(player, part);
    if (!healed) {
      return false;
    }
    boolean severityChanged = update(ModMoodles.PAIN, painTracker.currentSeverity());
    scoreboardDirty = true;
    return severityChanged;
  }

  /**
   * Called every tick to advance timers and react to the player's current
   * condition.
   */
  public boolean tick(ServerPlayerEntity player) {
    boolean hungerSeverityChanged = hungerTracker.tick(player);
    boolean hungerMoodleChanged = update(ModMoodles.HUNGER, hungerTracker.hungerSeverity());
    boolean satietyMoodleChanged = update(ModMoodles.SATIETY, hungerTracker.satietySeverity());
    if (hungerSeverityChanged || hungerMoodleChanged || satietyMoodleChanged) {
      hungerDirty = true;
      scoreboardDirty = true;
    }

    float healingModifier = hungerTracker.healingModifier();
    boolean painStateChanged = painTracker.tick(player, healingModifier);
    boolean painSeverityChanged = update(ModMoodles.PAIN, painTracker.currentSeverity());
    scoreboardDirty |= (painStateChanged || painSeverityChanged);

    return hungerMoodleChanged || satietyMoodleChanged || painSeverityChanged;
  }

  private boolean update(Moodle moodle, MoodleSeverity newSeverity) {
    return set(moodle, newSeverity);
  }

  public void flush(ServerPlayerEntity player) {
    if (hungerDirty) {
      hungerTracker.sync(player.getHungerManager());
      hungerDirty = false;
    }
  }

  public Map<BodyPart, Float> painSnapshot() {
    return painTracker.snapshot();
  }

  public float currentNutrition() {
    return hungerTracker.currentNutrition();
  }

  public boolean consumeScoreboardDirtyFlag() {
    boolean dirty = scoreboardDirty;
    scoreboardDirty = false;
    return dirty;
  }

  public void onPlayerAttached() {
    hungerTracker.markDirty();
    hungerDirty = true;
    scoreboardDirty = true;
  }

  public void onPlayerDetached() {
    scoreboardDirty = true;
  }

  public boolean canAlwaysEat() {
    return hungerTracker.canAlwaysEat();
  }

  public NbtCompound writeNbt() {
    NbtCompound nbt = new NbtCompound();
    nbt.put("Hunger", hungerTracker.writeNbt());
    nbt.put("Pain", painTracker.writeNbt());

    NbtCompound moodles = new NbtCompound();
    for (Map.Entry<Moodle, MoodleSeverity> entry : severities.entrySet()) {
      moodles.putString(entry.getKey().id().toString(), entry.getValue().name());
    }
    nbt.put("Severities", moodles);
    return nbt;
  }

  public void readNbt(NbtCompound nbt) {
    initializeSeverities();
    if (nbt != null) {
      if (nbt.contains("Hunger", NbtElement.COMPOUND_TYPE)) {
        hungerTracker.readNbt(nbt.getCompound("Hunger"));
      } else {
        hungerTracker.readNbt(null);
      }
      if (nbt.contains("Pain", NbtElement.COMPOUND_TYPE)) {
        painTracker.readNbt(nbt.getCompound("Pain"));
      } else {
        painTracker.readNbt(null);
      }
      if (nbt.contains("Severities", NbtElement.COMPOUND_TYPE)) {
        NbtCompound moodles = nbt.getCompound("Severities");
        for (String key : moodles.getKeys()) {
          Identifier id = Identifier.tryParse(key);
          if (id == null) {
            continue;
          }
          Moodle moodle = ModMoodles.get(id);
          if (moodle == null) {
            continue;
          }
          String name = moodles.getString(key);
          try {
            severities.put(moodle, MoodleSeverity.valueOf(name));
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    } else {
      hungerTracker.readNbt(null);
      painTracker.readNbt(null);
    }

    severities.put(ModMoodles.HUNGER, hungerTracker.hungerSeverity());
    severities.put(ModMoodles.SATIETY, hungerTracker.satietySeverity());
    severities.put(ModMoodles.PAIN, painTracker.currentSeverity());
    hungerTracker.markDirty();
    hungerDirty = true;
    scoreboardDirty = true;
  }

  private void initializeSeverities() {
    severities.clear();
    ModMoodles.values().forEach(moodle -> severities.put(moodle, MoodleSeverity.NONE));
  }
}
