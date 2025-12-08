package net.imsanty.moodles.moodle.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imsanty.moodles.moodle.ModMoodles;
import net.imsanty.moodles.moodle.Moodle;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.imsanty.moodles.moodle.network.SyncMoodlesPayload;
import net.minecraft.client.MinecraftClient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Maintains the client-side view of moodles for HUD rendering.
 */
public final class MoodleClientState {
  private static final Map<Moodle, MoodleSeverity> ACTIVE = new LinkedHashMap<>();

  private MoodleClientState() {
  }

  public static void init() {
    ModMoodles.values().forEach(moodle -> ACTIVE.put(moodle, MoodleSeverity.NONE));
    ClientPlayNetworking.registerGlobalReceiver(SyncMoodlesPayload.ID,
        (payload, context) -> {
          Map<Moodle, MoodleSeverity> incoming = new LinkedHashMap<>();
          payload.moodles().forEach((id, severity) -> {
            Moodle moodle = ModMoodles.get(id);
            if (moodle != null) {
              incoming.put(moodle, severity);
            }
          });
          context.client().execute(() -> applySnapshot(incoming));
        });
  }

  public static Map<Moodle, MoodleSeverity> getActive() {
    return Collections.unmodifiableMap(ACTIVE);
  }

  public static void applySnapshot(Map<Moodle, MoodleSeverity> snapshot) {
    ACTIVE.replaceAll((moodle, ignored) -> snapshot.getOrDefault(moodle, MoodleSeverity.NONE));
  }

  public static void clear() {
    ACTIVE.replaceAll((moodle, severity) -> MoodleSeverity.NONE);
  }

  public static void onClientStopped() {
    MinecraftClient.getInstance().execute(MoodleClientState::clear);
  }
}
