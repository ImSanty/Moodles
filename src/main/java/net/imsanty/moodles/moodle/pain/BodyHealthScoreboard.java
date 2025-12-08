package net.imsanty.moodles.moodle.pain;

import java.util.Map;

import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Maintains a sidebar scoreboard showing per-body-part health percentages for
 * debugging.
 */
public final class BodyHealthScoreboard {
  private static final String OBJECTIVE_NAME = "moodles_body";
  private static final String DISPLAY_COMMAND = "scoreboard objectives setdisplay sidebar " + OBJECTIVE_NAME;
  private static final String CREATE_COMMAND = "scoreboard objectives add " + OBJECTIVE_NAME
      + " dummy {\"translate\":\"scoreboard.moodles.body_health\"}";
  private static final String NUMBER_FORMAT_COMMAND = "scoreboard objectives modify " + OBJECTIVE_NAME
      + " numberformat integer";
  private static final String HUNGER_ENTRY_SUFFIX = "hunger";

  private BodyHealthScoreboard() {
  }

  public static void ensureObjective(MinecraftServer server) {
    Scoreboard scoreboard = server.getScoreboard();
    if (scoreboard.getNullableObjective(OBJECTIVE_NAME) != null) {
      return;
    }
    CommandManager manager = server.getCommandManager();
    ServerCommandSource source = server.getCommandSource().withLevel(4).withSilent();
    manager.executeWithPrefix(source, CREATE_COMMAND);
    manager.executeWithPrefix(source, NUMBER_FORMAT_COMMAND);
    manager.executeWithPrefix(source, DISPLAY_COMMAND);
  }

  public static void update(ServerPlayerEntity player, Map<BodyPart, Float> healthSnapshot, float hungerNutrition) {
    MinecraftServer server = player.getServer();
    ensureObjective(server);

    Scoreboard scoreboard = server.getScoreboard();
    ScoreboardObjective objective = scoreboard.getNullableObjective(OBJECTIVE_NAME);
    if (objective == null) {
      return;
    }

    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
    for (Map.Entry<BodyPart, Float> entry : healthSnapshot.entrySet()) {
      BodyPart part = entry.getKey();
      float value = entry.getValue();
      int percent = Math.round((value / part.maxHealth()) * 100.0f);
      String key = entryKey(player, part);
      ScoreHolder holder = ScoreHolder.fromName(key);
      ScoreAccess score = scoreboard.getOrCreateScore(holder, objective);
      score.setScore(percent);
      score.setDisplayText(Text.literal(player.getGameProfile().getName() + " - " + part.displayName().getString()));
    }

    String hungerKey = entryKey(player, HUNGER_ENTRY_SUFFIX);
    ScoreHolder hungerHolder = ScoreHolder.fromName(hungerKey);
    ScoreAccess hungerScore = scoreboard.getOrCreateScore(hungerHolder, objective);
    hungerScore.setScore(Math.round(hungerNutrition));
    hungerScore.setDisplayText(Text.literal(player.getGameProfile().getName() + " - Hunger Value"));
  }

  public static void clear(ServerPlayerEntity player) {
    MinecraftServer server = player.getServer();
    Scoreboard scoreboard = server.getScoreboard();
    ScoreboardObjective objective = scoreboard.getNullableObjective(OBJECTIVE_NAME);
    if (objective == null) {
      return;
    }
    for (BodyPart part : BodyPart.values()) {
      String key = entryKey(player, part);
      ScoreHolder holder = ScoreHolder.fromName(key);
      scoreboard.removeScore(holder, objective);
    }
    scoreboard.removeScore(ScoreHolder.fromName(entryKey(player, HUNGER_ENTRY_SUFFIX)), objective);
  }

  private static String entryKey(ServerPlayerEntity player, BodyPart part) {
    String uuid = player.getUuidAsString().substring(0, 8);
    return uuid + ":" + part.shortName();
  }

  private static String entryKey(ServerPlayerEntity player, String suffix) {
    String uuid = player.getUuidAsString().substring(0, 8);
    return uuid + ":" + suffix;
  }
}
