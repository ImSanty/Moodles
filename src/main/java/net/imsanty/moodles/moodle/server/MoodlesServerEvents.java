package net.imsanty.moodles.moodle.server;

import net.imsanty.moodles.moodle.network.ApplyBandagePayload;
import net.imsanty.moodles.moodle.player.PlayerMoodleManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Hooks server lifecycle events so the moodle system can keep in step with
 * player state.
 */
public final class MoodlesServerEvents {
  private MoodlesServerEvents() {
  }

  public static void register() {
    ServerLifecycleEvents.SERVER_STARTED.register(MoodlesServerEvents::onServerStarted);
    ServerPlayConnectionEvents.JOIN
        .register((handler, sender, server) -> PlayerMoodleManager.handleJoin(handler.player));
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerMoodleManager.remove(handler.player));
    ServerTickEvents.END_SERVER_TICK.register(MoodlesServerEvents::tickPlayers);
    ServerPlayerEvents.COPY_FROM
        .register((oldPlayer, newPlayer, alive) -> PlayerMoodleManager.transfer(oldPlayer, newPlayer));
    ServerLivingEntityEvents.AFTER_DAMAGE.register(MoodlesServerEvents::onAfterDamage);
    ServerPlayNetworking.registerGlobalReceiver(ApplyBandagePayload.ID,
        (payload, context) -> {
          ServerPlayerEntity player = context.player();
          if (player == null) {
            return;
          }
          player.server.execute(() -> PlayerMoodleManager.applyBandage(player, payload.part(), payload.hand()));
        });
  }

  private static void onServerStarted(MinecraftServer server) {
    server.getPlayerManager().getPlayerList().forEach(PlayerMoodleManager::handleJoin);
  }

  private static void tickPlayers(MinecraftServer server) {
    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
      PlayerMoodleManager.tick(player);
    }
  }

  private static void onAfterDamage(LivingEntity entity, net.minecraft.entity.damage.DamageSource source,
      float originalAmount, float actualAmount, boolean blocked) {
    if (blocked || actualAmount <= 0) {
      return;
    }
    if (entity instanceof ServerPlayerEntity player) {
      PlayerMoodleManager.onDamage(player, source, actualAmount);
    }
  }
}
