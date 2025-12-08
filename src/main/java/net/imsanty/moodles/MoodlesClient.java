package net.imsanty.moodles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.imsanty.moodles.item.ModItems;
import net.imsanty.moodles.moodle.client.BodyHealthClientState;
import net.imsanty.moodles.moodle.client.BodyHealthScreen;
import net.imsanty.moodles.moodle.client.MoodleClientState;
import net.imsanty.moodles.moodle.client.MoodlesHudOverlay;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class MoodlesClient implements ClientModInitializer {
  private static KeyBinding bodyHealthKey;

  @Override
  public void onInitializeClient() {
    MoodleClientState.init();
    BodyHealthClientState.init();
    MoodlesHudOverlay.register();

    bodyHealthKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.moodles.body_health",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.moodles"));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.player == null) {
        return;
      }
      while (bodyHealthKey.wasPressed()) {
        if (client.currentScreen instanceof BodyHealthScreen) {
          client.setScreen(null);
          continue;
        }
        Hand treatmentHand = null;
        ItemStack snapshot = ItemStack.EMPTY;
        if (client.player.getMainHandStack().isOf(ModItems.BANDAGE)) {
          treatmentHand = Hand.MAIN_HAND;
          snapshot = client.player.getMainHandStack().copy();
        } else if (client.player.getOffHandStack().isOf(ModItems.BANDAGE)) {
          treatmentHand = Hand.OFF_HAND;
          snapshot = client.player.getOffHandStack().copy();
        }
        BodyHealthScreen.open(treatmentHand, snapshot);
      }
    });
  }

  public static KeyBinding bodyHealthKeyBinding() {
    return bodyHealthKey;
  }

}
