package net.imsanty.moodles.moodle.hunger;

import net.imsanty.moodles.duck.HungerManagerBridge;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Project Zomboid inspired hunger model that separates hunger penalty moodles
 * from positive satiety moodles while ensuring players can always eat.
 */
public final class PlayerHungerTracker {
  private static final float IDEAL_NUTRITION = 110.0f;
  private static final float MAX_NUTRITION = 160.0f;

  private static final float BASE_DECAY_PER_TICK = 0.0012f;
  private static final float SPRINT_DECAY = 0.0024f;
  private static final float SWIM_DECAY = 0.0018f;
  private static final float NIGHT_DECAY = 0.00035f;
  private static final float EXHAUSTION_TO_NUTRITION = 2.0f;

  private static final float FOOD_NUTRITION_MULTIPLIER = 6.0f;
  private static final float FOOD_SATURATION_MULTIPLIER = 12.0f;

  private float nutrition = IDEAL_NUTRITION;
  private MoodleSeverity hungerSeverity = MoodleSeverity.NONE;
  private MoodleSeverity satietySeverity = MoodleSeverity.NONE;
  private boolean dirty = true;
  private int lastSyncedFoodLevel = 20;

  public boolean tick(ServerPlayerEntity player) {
    float decay = BASE_DECAY_PER_TICK;
    if (player.isSprinting()) {
      decay += SPRINT_DECAY;
    }
    if (player.isSwimming()) {
      decay += SWIM_DECAY;
    }
    if (player.getWorld().isNight()) {
      decay += NIGHT_DECAY;
    }

    nutrition = MathHelper.clamp(nutrition - decay, 0.0f, MAX_NUTRITION);
    boolean changed = recalcSeverities();
    if (changed) {
      dirty = true;
    }
    return changed;
  }

  public void onExhaustion(ServerPlayerEntity player, float amount) {
    if (amount <= 0.0f) {
      return;
    }
    nutrition = MathHelper.clamp(nutrition - amount * EXHAUSTION_TO_NUTRITION, 0.0f, MAX_NUTRITION);
    if (recalcSeverities()) {
      dirty = true;
    }
  }

  public void onEat(ItemStack stack, FoodComponent food) {
    if (food == null) {
      return;
    }
    float gain = FoodBalance.computeGain(stack, food, FOOD_NUTRITION_MULTIPLIER, FOOD_SATURATION_MULTIPLIER);
    nutrition = MathHelper.clamp(nutrition + gain, 0.0f, MAX_NUTRITION);
    if (recalcSeverities()) {
      dirty = true;
    }
    dirty = true;
  }

  public MoodleSeverity hungerSeverity() {
    return hungerSeverity;
  }

  public MoodleSeverity satietySeverity() {
    return satietySeverity;
  }

  public float currentNutrition() {
    return nutrition;
  }

  public float healingModifier() {
    float modifier = 1.0f;
    switch (hungerSeverity) {
      case MINOR -> modifier *= 0.8f;
      case MODERATE -> modifier *= 0.55f;
      case MAJOR -> modifier *= 0.35f;
      case CRITICAL -> modifier *= 0.1f;
      default -> {
      }
    }

    float satietyBonus = switch (satietySeverity) {
      case MINOR -> 1.15f;
      case MODERATE -> 1.35f;
      case MAJOR -> 1.65f;
      case CRITICAL -> 2.0f;
      default -> 1.0f;
    };
    modifier *= satietyBonus;
    return MathHelper.clamp(modifier, 0.1f, 2.0f);
  }

  public void markDirty() {
    dirty = true;
  }

  public void sync(HungerManager manager) {
    if (!dirty) {
      return;
    }

    HungerManagerBridge accessor = (HungerManagerBridge) manager;
    float clampedForBar = MathHelper.clamp(nutrition, 0.0f, IDEAL_NUTRITION);
    int targetFoodLevel = MathHelper.clamp(Math.round((clampedForBar / IDEAL_NUTRITION) * 20.0f), 0, 20);

    accessor.moodles$setPrevFoodLevel(lastSyncedFoodLevel);
    manager.setFoodLevel(targetFoodLevel);
    float saturation = MathHelper.clamp((nutrition / IDEAL_NUTRITION) * 6.0f, 0.0f, 6.0f);
    manager.setSaturationLevel(saturation);
    manager.setExhaustion(0.0f);
    accessor.moodles$setFoodTickTimer(0);

    lastSyncedFoodLevel = targetFoodLevel;
    dirty = false;
  }

  public boolean canAlwaysEat() {
    return true;
  }

  public NbtCompound writeNbt() {
    NbtCompound nbt = new NbtCompound();
    nbt.putFloat("Nutrition", nutrition);
    nbt.putInt("LastFoodLevel", lastSyncedFoodLevel);
    return nbt;
  }

  public void readNbt(NbtCompound nbt) {
    if (nbt != null) {
      if (nbt.contains("Nutrition", NbtElement.FLOAT_TYPE)) {
        nutrition = MathHelper.clamp(nbt.getFloat("Nutrition"), 0.0f, MAX_NUTRITION);
      } else {
        nutrition = IDEAL_NUTRITION;
      }
      if (nbt.contains("LastFoodLevel", NbtElement.INT_TYPE)) {
        lastSyncedFoodLevel = MathHelper.clamp(nbt.getInt("LastFoodLevel"), 0, 20);
      } else {
        lastSyncedFoodLevel = 20;
      }
    } else {
      nutrition = IDEAL_NUTRITION;
      lastSyncedFoodLevel = 20;
    }
    recalcSeverities();
    dirty = true;
  }

  private boolean recalcSeverities() {
    MoodleSeverity newHunger = computeHungerSeverity();
    MoodleSeverity newSatiety = computeSatietySeverity();
    boolean changed = newHunger != hungerSeverity || newSatiety != satietySeverity;
    hungerSeverity = newHunger;
    satietySeverity = newSatiety;
    return changed;
  }

  private MoodleSeverity computeHungerSeverity() {
    float ratio = nutrition / IDEAL_NUTRITION;
    if (ratio >= 0.8f) {
      return MoodleSeverity.NONE;
    }
    if (ratio >= 0.6f) {
      return MoodleSeverity.MINOR;
    }
    if (ratio >= 0.45f) {
      return MoodleSeverity.MODERATE;
    }
    if (ratio >= 0.3f) {
      return MoodleSeverity.MAJOR;
    }
    return MoodleSeverity.CRITICAL;
  }

  private MoodleSeverity computeSatietySeverity() {
    float ratio = nutrition / MAX_NUTRITION;
    if (ratio < 0.7f) {
      return MoodleSeverity.NONE;
    }
    if (ratio < 0.8f) {
      return MoodleSeverity.MINOR;
    }
    if (ratio < 0.9f) {
      return MoodleSeverity.MODERATE;
    }
    if (ratio < 1.0f) {
      return MoodleSeverity.MAJOR;
    }
    return MoodleSeverity.CRITICAL;
  }
}
