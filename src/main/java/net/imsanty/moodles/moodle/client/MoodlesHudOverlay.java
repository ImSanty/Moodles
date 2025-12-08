package net.imsanty.moodles.moodle.client;

import net.imsanty.moodles.moodle.Moodle;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Minimal HUD overlay that lists active moodles and their severity. Icons will
 * come later.
 */
public final class MoodlesHudOverlay implements HudRenderCallback {
  private static final int START_X = 8;
  private static final int START_Y = 8;

  private MoodlesHudOverlay() {
  }

  public static void register() {
    HudRenderCallback.EVENT.register(new MoodlesHudOverlay());
  }

  @Override
  public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null || client.options.hudHidden) {
      return;
    }

    int offsetY = 0;
    int lineHeight = client.textRenderer.fontHeight + 2;
    for (var entry : MoodleClientState.getActive().entrySet()) {
      Moodle moodle = entry.getKey();
      MoodleSeverity severity = entry.getValue();
      if (!severity.isActive()) {
        continue;
      }
      Text line = Text.empty()
          .append(moodle.displayName())
          .append(Text.literal(" "))
          .append(Text.literal("[" + severity.name() + "]"));
      context.drawText(client.textRenderer, line, START_X, START_Y + offsetY, 0xFFFFFF, true);
      offsetY += lineHeight;
    }
  }
}
