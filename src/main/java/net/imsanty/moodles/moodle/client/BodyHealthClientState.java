package net.imsanty.moodles.moodle.client;

import java.util.EnumMap;
import java.util.Map;

import net.imsanty.moodles.moodle.network.SyncBodyHealthPayload;
import net.imsanty.moodles.moodle.pain.BodyPart;
import net.minecraft.util.math.MathHelper;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class BodyHealthClientState {
  private static final EnumMap<BodyPart, Float> HEALTH = new EnumMap<>(BodyPart.class);

  private BodyHealthClientState() {
  }

  public static void init() {
    for (BodyPart part : BodyPart.values()) {
      HEALTH.put(part, part.maxHealth());
    }
    ClientPlayNetworking.registerGlobalReceiver(SyncBodyHealthPayload.ID,
        (payload, context) -> context.client().execute(() -> applySnapshot(payload.health())));
  }

  public static Map<BodyPart, Float> snapshot() {
    return new EnumMap<>(HEALTH);
  }

  public static float current(BodyPart part) {
    return HEALTH.getOrDefault(part, part.maxHealth());
  }

  public static float ratio(BodyPart part) {
    return MathHelper.clamp(current(part) / part.maxHealth(), 0.0f, 1.0f);
  }

  private static void applySnapshot(Map<BodyPart, Float> snapshot) {
    HEALTH.putAll(snapshot);
  }
}
