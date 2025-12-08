package net.imsanty.moodles.moodle.debug;

import java.util.Map;
import java.util.stream.Collectors;

import net.imsanty.moodles.moodle.Moodle;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.imsanty.moodles.moodle.player.PlayerMoodleManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.mojang.brigadier.context.CommandContext;

/**
 * Provides a simple debug flag that mirrors current moodles in the action bar.
 */
public final class MoodlesDebug {
  private static boolean enabled = false;

  private MoodlesDebug() {
  }

  public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
        CommandManager.literal("moodles")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("debug")
                .executes(context -> toggle(context))
                .then(CommandManager.literal("on").executes(context -> setEnabled(context, true)))
                .then(CommandManager.literal("off").executes(context -> setEnabled(context, false))))));
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static void updateDisplay(ServerPlayerEntity player, Map<Moodle, MoodleSeverity> snapshot) {
    if (!enabled) {
      return;
    }

    String joined = snapshot.entrySet().stream()
        .filter(entry -> entry.getValue().isActive())
        .map(entry -> formatEntry(entry.getKey(), entry.getValue()))
        .collect(Collectors.joining("  |  "));

    MutableText message = joined.isEmpty()
        ? Text.translatable("debug.moodles.none")
        : Text.literal(joined);
    player.sendMessage(message, true);
  }

  private static int toggle(CommandContext<ServerCommandSource> context) {
    return setEnabled(context, !enabled);
  }

  private static int setEnabled(CommandContext<ServerCommandSource> context, boolean value) {
    ServerCommandSource source = context.getSource();
    if (enabled == value) {
      source.sendFeedback(() -> Text.translatable(value ? "commands.moodles.debug.already_enabled"
          : "commands.moodles.debug.already_disabled"), false);
      return value ? 1 : 0;
    }

    enabled = value;
    if (enabled) {
      source.getServer().getPlayerManager().getPlayerList().forEach(PlayerMoodleManager::sync);
      source.sendFeedback(() -> Text.translatable("commands.moodles.debug.enabled"), true);
    } else {
      source.sendFeedback(() -> Text.translatable("commands.moodles.debug.disabled"), true);
    }
    return value ? 1 : 0;
  }

  private static String formatEntry(Moodle moodle, MoodleSeverity severity) {
    return moodle.displayName().getString() + ": " + severity.name();
  }
}
