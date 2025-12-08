package net.imsanty.moodles.moodle.pain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.imsanty.moodles.moodle.MoodleSeverity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

/**
 * Tracks per-player damage across body parts and derives the Pain moodle
 * severity.
 */
public final class PlayerPainTracker {
  private static final int NATURAL_REGEN_DELAY_TICKS = 200; // 10 seconds without damage
  private static final int FULL_HEAL_TICKS = 3 * 24000; // three in-game days

  private final EnumMap<BodyPart, Float> health = new EnumMap<>(BodyPart.class);
  private int ticksSinceDamage = NATURAL_REGEN_DELAY_TICKS;
  private MoodleSeverity currentSeverity = MoodleSeverity.NONE;
  private int debuffRefreshTimer = 0;

  public PlayerPainTracker() {
    for (BodyPart part : BodyPart.values()) {
      health.put(part, part.maxHealth());
    }
  }

  public boolean onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
    if (amount <= 0) {
      return false;
    }

    ticksSinceDamage = 0;
    EnumMap<BodyPart, Float> weights = determineDamageWeights(player, source);
    float bodyDamage = computeBodyDamage(player, amount);
    boolean changed = applyDistributedDamage(bodyDamage, weights, player.getWorld().random);
    reconcilePlayerHealth(player);
    changed |= updateSeverity(player);
    return changed;
  }

  public boolean tick(ServerPlayerEntity player, float healingModifier) {
    ticksSinceDamage++;
    boolean healed = false;

    int regenDelay = Math.max(40,
        MathHelper.floor(NATURAL_REGEN_DELAY_TICKS / MathHelper.clamp(healingModifier, 0.35f, 2.0f)));
    if (ticksSinceDamage >= regenDelay) {
      healed |= healTowardsMax(player, healingModifier);
    }

    healed |= synchronizeWithActualHealth(player);
    healed |= updateSeverity(player);
    refreshDebuffs(player);
    return healed;
  }

  public MoodleSeverity currentSeverity() {
    return currentSeverity;
  }

  public Map<BodyPart, Float> snapshot() {
    return Collections.unmodifiableMap(health);
  }

  public boolean applyBandage(ServerPlayerEntity player, BodyPart part) {
    float current = health.getOrDefault(part, part.maxHealth());
    float missing = part.maxHealth() - current;
    if (missing <= 0.05f) {
      return false;
    }
    float healAmount = Math.max(part.maxHealth() * 0.35f, 2.5f);
    float newValue = Math.min(part.maxHealth(), current + healAmount);
    if (newValue <= current + 0.01f) {
      return false;
    }
    health.put(part, newValue);
    ticksSinceDamage = Math.max(ticksSinceDamage, NATURAL_REGEN_DELAY_TICKS / 2);
    reconcilePlayerHealth(player);
    updateSeverity(player);
    return true;
  }

  public NbtCompound writeNbt() {
    NbtCompound nbt = new NbtCompound();
    NbtCompound healthNbt = new NbtCompound();
    for (BodyPart part : BodyPart.values()) {
      healthNbt.putFloat(part.name(), health.getOrDefault(part, part.maxHealth()));
    }
    nbt.put("Health", healthNbt);
    nbt.putInt("TicksSinceDamage", ticksSinceDamage);
    nbt.putString("Severity", currentSeverity.name());
    return nbt;
  }

  public void readNbt(NbtCompound nbt) {
    if (nbt == null) {
      reset();
      return;
    }
    NbtCompound healthNbt = nbt.getCompound("Health");
    for (BodyPart part : BodyPart.values()) {
      float value = part.maxHealth();
      if (healthNbt.contains(part.name(), NbtElement.FLOAT_TYPE)) {
        value = MathHelper.clamp(healthNbt.getFloat(part.name()), 0.0f, part.maxHealth());
      }
      health.put(part, value);
    }
    ticksSinceDamage = MathHelper.clamp(nbt.getInt("TicksSinceDamage"), 0, FULL_HEAL_TICKS);
    String severity = nbt.getString("Severity");
    try {
      currentSeverity = MoodleSeverity.valueOf(severity);
    } catch (IllegalArgumentException ignored) {
      currentSeverity = MoodleSeverity.NONE;
    }
    debuffRefreshTimer = 0;
  }

  private void reset() {
    for (BodyPart part : BodyPart.values()) {
      health.put(part, part.maxHealth());
    }
    ticksSinceDamage = NATURAL_REGEN_DELAY_TICKS;
    currentSeverity = MoodleSeverity.NONE;
    debuffRefreshTimer = 0;
  }

  private EnumMap<BodyPart, Float> determineDamageWeights(ServerPlayerEntity player, DamageSource source) {
    EnumMap<BodyPart, Float> weights = new EnumMap<>(BodyPart.class);
    Random random = player.getWorld().random;

    if (source.isIn(DamageTypeTags.IS_FALL)) {
      weights.put(BodyPart.LEFT_LEG, 0.45f);
      weights.put(BodyPart.RIGHT_LEG, 0.45f);
      weights.put(BodyPart.TORSO, 0.10f);
      return normalizeWeights(weights);
    }

    if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
      weights.put(BodyPart.TORSO, 0.30f);
      weights.put(BodyPart.HEAD, 0.18f);
      weights.put(BodyPart.LEFT_ARM, 0.12f);
      weights.put(BodyPart.RIGHT_ARM, 0.12f);
      weights.put(BodyPart.LEFT_LEG, 0.14f);
      weights.put(BodyPart.RIGHT_LEG, 0.14f);
      return normalizeWeights(weights);
    }

    if (source.isIn(DamageTypeTags.WITCH_RESISTANT_TO)) {
      weights.put(BodyPart.TORSO, 0.7f);
      weights.put(BodyPart.HEAD, 0.3f);
      return normalizeWeights(weights);
    }

    Vec3d impact = resolveImpactPosition(player, source);
    if (impact != null) {
      BodyPart primary = classifyImpact(player, impact, random);
      assignWeightedHit(weights, primary);
      return normalizeWeights(weights);
    }

    Entity attacker = source.getAttacker();
    if (attacker instanceof net.minecraft.entity.LivingEntity living) {
      Vec3d attackPos = living.getEyePos();
      BodyPart primary = classifyImpact(player, attackPos, random);
      assignWeightedHit(weights, primary);
      return normalizeWeights(weights);
    }

    assignWeightedHit(weights, BodyPart.TORSO);
    return normalizeWeights(weights);
  }

  private void assignWeightedHit(EnumMap<BodyPart, Float> weights, BodyPart primary) {
    switch (primary) {
      case HEAD -> {
        weights.put(BodyPart.HEAD, 0.7f);
        weights.put(BodyPart.TORSO, 0.3f);
      }
      case TORSO -> {
        weights.put(BodyPart.TORSO, 0.65f);
        weights.put(BodyPart.HEAD, 0.15f);
        weights.put(BodyPart.LEFT_ARM, 0.10f);
        weights.put(BodyPart.RIGHT_ARM, 0.10f);
      }
      case LEFT_ARM -> {
        weights.put(BodyPart.LEFT_ARM, 0.6f);
        weights.put(BodyPart.TORSO, 0.3f);
        weights.put(BodyPart.RIGHT_ARM, 0.1f);
      }
      case RIGHT_ARM -> {
        weights.put(BodyPart.RIGHT_ARM, 0.6f);
        weights.put(BodyPart.TORSO, 0.3f);
        weights.put(BodyPart.LEFT_ARM, 0.1f);
      }
      case LEFT_LEG -> {
        weights.put(BodyPart.LEFT_LEG, 0.65f);
        weights.put(BodyPart.RIGHT_LEG, 0.2f);
        weights.put(BodyPart.TORSO, 0.15f);
      }
      case RIGHT_LEG -> {
        weights.put(BodyPart.RIGHT_LEG, 0.65f);
        weights.put(BodyPart.LEFT_LEG, 0.2f);
        weights.put(BodyPart.TORSO, 0.15f);
      }
    }
  }

  private EnumMap<BodyPart, Float> normalizeWeights(EnumMap<BodyPart, Float> weights) {
    float sum = 0.0f;
    for (float value : weights.values()) {
      sum += value;
    }
    if (sum <= 0.0f) {
      weights.clear();
      weights.put(BodyPart.TORSO, 1.0f);
      return weights;
    }
    for (Map.Entry<BodyPart, Float> entry : weights.entrySet()) {
      entry.setValue(entry.getValue() / sum);
    }
    return weights;
  }

  private BodyPart classifyImpact(ServerPlayerEntity player, Vec3d impact, Random random) {
    Box box = player.getBoundingBox();
    double clampedY = MathHelper.clamp(impact.y, box.minY, box.maxY);
    double normalizedY = player.getHeight() <= 0.0 ? 0.5
        : (clampedY - box.minY) / player.getHeight();

    if (normalizedY >= 0.82) {
      return BodyPart.HEAD;
    }
    if (normalizedY >= 0.55) {
      return BodyPart.TORSO;
    }
    if (normalizedY >= 0.38) {
      return pickSide(player, impact, random, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM);
    }
    return pickSide(player, impact, random, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG);
  }

  private BodyPart pickSide(ServerPlayerEntity player, Vec3d impact, Random random, BodyPart left, BodyPart right) {
    Vec3d offset = new Vec3d(impact.x - player.getX(), 0, impact.z - player.getZ());
    if (offset.lengthSquared() <= 0.0001f) {
      return random.nextBoolean() ? left : right;
    }
    float yawRad = (float) Math.toRadians(player.getYaw());
    Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
    Vec3d rightVec = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
    if (rightVec.lengthSquared() <= 0.0001f) {
      return random.nextBoolean() ? left : right;
    }
    rightVec = rightVec.normalize();
    double side = offset.dotProduct(rightVec);
    if (Math.abs(side) <= 0.0001f) {
      return random.nextBoolean() ? left : right;
    }
    return side >= 0 ? right : left;
  }

  private Vec3d resolveImpactPosition(ServerPlayerEntity player, DamageSource source) {
    Vec3d pos = source.getPosition();
    if (pos != null) {
      return pos;
    }
    Entity direct = source.getSource();
    if (direct != null) {
      if (direct instanceof ProjectileEntity projectile) {
        return projectile.getPos();
      }
      return direct.getEyePos();
    }
    Entity attacker = source.getAttacker();
    if (attacker != null) {
      return attacker.getEyePos();
    }
    return player.getPos().add(0.0, player.getStandingEyeHeight(), 0.0);
  }

  private float computeBodyDamage(ServerPlayerEntity player, float amount) {
    float totalMax = totalMaxHealth();
    float playerMax = Math.max(player.getMaxHealth(), 1.0f);
    float scaled = (amount / playerMax) * totalMax;
    return MathHelper.clamp(scaled, 0.0f, totalCurrentHealth());
  }

  private boolean applyDistributedDamage(float amount, EnumMap<BodyPart, Float> weights, Random random) {
    if (amount <= 0.0001f) {
      return false;
    }
    float remaining = amount;
    boolean changed = false;
    int guard = 0;

    while (remaining > 0.0001f && guard++ < 8) {
      float totalWeight = 0.0f;
      for (Map.Entry<BodyPart, Float> entry : weights.entrySet()) {
        BodyPart part = entry.getKey();
        float weight = entry.getValue();
        if (weight <= 0.0f) {
          continue;
        }
        if (health.get(part) <= 0.0001f) {
          continue;
        }
        totalWeight += weight;
      }
      if (totalWeight <= 0.0f) {
        break;
      }

      for (Map.Entry<BodyPart, Float> entry : weights.entrySet()) {
        BodyPart part = entry.getKey();
        float weight = entry.getValue();
        if (weight <= 0.0f) {
          continue;
        }
        float current = health.get(part);
        if (current <= 0.0001f) {
          continue;
        }
        float portion = (weight / totalWeight) * remaining;
        float applied = Math.min(current, portion);
        if (applied <= 0.0001f) {
          continue;
        }
        health.put(part, current - applied);
        remaining -= applied;
        changed = true;
        if (remaining <= 0.0001f) {
          break;
        }
      }
    }

    if (remaining > 0.0001f) {
      BodyPart fallback = BodyPart.TORSO;
      float current = health.get(fallback);
      float applied = Math.min(current, remaining);
      if (applied > 0.0f) {
        health.put(fallback, current - applied);
        remaining -= applied;
        changed = true;
      }
    }

    if (remaining > 0.0001f) {
      BodyPart[] parts = BodyPart.values();
      BodyPart randomPart = parts[random.nextInt(parts.length)];
      float current = health.get(randomPart);
      float applied = Math.min(current, remaining);
      if (applied > 0.0f) {
        health.put(randomPart, current - applied);
        changed = true;
      }
    }
    return changed;
  }

  private void reconcilePlayerHealth(ServerPlayerEntity player) {
    float ratio = totalCurrentHealth() / Math.max(totalMaxHealth(), 0.0001f);
    float newHealth = ratio * player.getMaxHealth();
    player.setHealth(MathHelper.clamp(newHealth, 0.0f, player.getMaxHealth()));
  }

  private boolean healTowardsMax(ServerPlayerEntity player, float healingModifier) {
    boolean changed = false;
    for (BodyPart part : BodyPart.values()) {
      float current = health.get(part);
      float missing = part.maxHealth() - current;
      if (missing <= 0.0001f) {
        continue;
      }
      float regenPerTick = (part.maxHealth() / FULL_HEAL_TICKS)
          * MathHelper.clamp(healingModifier, 0.25f, 2.25f);
      float healAmount = Math.min(missing, regenPerTick);
      if (healAmount > 0) {
        health.put(part, current + healAmount);
        changed = true;
      }
    }
    if (changed) {
      applyNaturalPlayerHealing(player, healingModifier);
    }
    return changed;
  }

  private boolean synchronizeWithActualHealth(ServerPlayerEntity player) {
    float playerRatio = player.getHealth() / player.getMaxHealth();
    float totalMax = totalMaxHealth();
    float currentTotal = totalCurrentHealth();
    float desiredTotal = MathHelper.clamp(playerRatio * totalMax, 0.0f, totalMax);
    if (desiredTotal <= currentTotal + 0.0001f) {
      return false;
    }
    float toDistribute = desiredTotal - currentTotal;
    distributeHealing(toDistribute);
    return toDistribute > 0.0001f;
  }

  private void distributeHealing(float amount) {
    if (amount <= 0) {
      return;
    }
    float remaining = amount;
    for (BodyPart part : BodyPart.values()) {
      if (remaining <= 0) {
        break;
      }
      float current = health.get(part);
      float missing = part.maxHealth() - current;
      if (missing <= 0) {
        continue;
      }
      float applied = Math.min(missing, remaining);
      health.put(part, current + applied);
      remaining -= applied;
    }
  }

  private boolean updateSeverity(ServerPlayerEntity player) {
    MoodleSeverity newSeverity = calculateSeverity();
    if (newSeverity != currentSeverity) {
      currentSeverity = newSeverity;
      applyDebuffCleanup(player);
      debuffRefreshTimer = 0; // force immediate refresh with new severity
      return true;
    }
    return false;
  }

  private MoodleSeverity calculateSeverity() {
    float worstRatio = 0.0f;
    float aggregateMissing = 0.0f;
    for (BodyPart part : BodyPart.values()) {
      float current = health.get(part);
      float missing = part.maxHealth() - current;
      aggregateMissing += missing;
      float ratio = missing / part.maxHealth();
      if (ratio > worstRatio) {
        worstRatio = ratio;
      }
    }
    float totalRatio = aggregateMissing / totalMaxHealth();

    if (worstRatio >= 0.75f || totalRatio >= 0.65f) {
      return MoodleSeverity.CRITICAL;
    }
    if (worstRatio >= 0.5f || totalRatio >= 0.45f) {
      return MoodleSeverity.MAJOR;
    }
    if (worstRatio >= 0.3f || totalRatio >= 0.25f) {
      return MoodleSeverity.MODERATE;
    }
    if (worstRatio >= 0.15f || totalRatio >= 0.1f) {
      return MoodleSeverity.MINOR;
    }
    return MoodleSeverity.NONE;
  }

  private void refreshDebuffs(ServerPlayerEntity player) {
    if (player.getWorld().isClient()) {
      return;
    }
    debuffRefreshTimer++;
    if (debuffRefreshTimer < 20) { // refresh once per second
      return;
    }
    debuffRefreshTimer = 0;

    switch (currentSeverity) {
      case NONE -> applyDebuffCleanup(player);
      case MINOR -> {
        applyEffect(player, StatusEffects.MINING_FATIGUE, 0);
        removeIfPresent(player, StatusEffects.SLOWNESS);
        removeIfPresent(player, StatusEffects.WEAKNESS);
        removeIfPresent(player, StatusEffects.NAUSEA);
      }
      case MODERATE -> {
        applyEffect(player, StatusEffects.MINING_FATIGUE, 1);
        applyEffect(player, StatusEffects.WEAKNESS, 0);
        removeIfPresent(player, StatusEffects.SLOWNESS);
        removeIfPresent(player, StatusEffects.NAUSEA);
      }
      case MAJOR -> {
        applyEffect(player, StatusEffects.MINING_FATIGUE, 2);
        applyEffect(player, StatusEffects.WEAKNESS, 1);
        applyEffect(player, StatusEffects.SLOWNESS, 1);
        removeIfPresent(player, StatusEffects.NAUSEA);
      }
      case CRITICAL -> {
        applyEffect(player, StatusEffects.MINING_FATIGUE, 3);
        applyEffect(player, StatusEffects.WEAKNESS, 2);
        applyEffect(player, StatusEffects.SLOWNESS, 3);
        applyEffect(player, StatusEffects.NAUSEA, 0);
      }
    }
  }

  private void applyDebuffCleanup(ServerPlayerEntity player) {
    if (currentSeverity.isActive()) {
      return;
    }
    removeIfPresent(player, StatusEffects.MINING_FATIGUE);
    removeIfPresent(player, StatusEffects.WEAKNESS);
    removeIfPresent(player, StatusEffects.SLOWNESS);
    removeIfPresent(player, StatusEffects.NAUSEA);
  }

  private void applyEffect(ServerPlayerEntity player,
      net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amplifier) {
    player.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
  }

  private void removeIfPresent(ServerPlayerEntity player,
      net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
    if (player.hasStatusEffect(effect)) {
      player.removeStatusEffect(effect);
    }
  }

  private void applyNaturalPlayerHealing(ServerPlayerEntity player, float healingModifier) {
    float totalMax = totalMaxHealth();
    float currentTotal = totalCurrentHealth();
    float targetHealth = (currentTotal / totalMax) * player.getMaxHealth();
    float diff = targetHealth - player.getHealth();
    if (diff > 0.01f) {
      float healCap = 0.5f * MathHelper.clamp(healingModifier, 0.3f, 1.8f);
      player.heal(Math.min(diff, healCap));
    }
  }

  private float totalMaxHealth() {
    float total = 0.0f;
    for (BodyPart part : BodyPart.values()) {
      total += part.maxHealth();
    }
    return total;
  }

  private float totalCurrentHealth() {
    float total = 0.0f;
    for (BodyPart part : BodyPart.values()) {
      total += health.get(part);
    }
    return total;
  }
}
