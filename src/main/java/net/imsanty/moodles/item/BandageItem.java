package net.imsanty.moodles.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.imsanty.moodles.moodle.client.BodyHealthScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BandageItem extends Item {
  public BandageItem(Settings settings) {
    super(settings);
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
    ItemStack stack = user.getStackInHand(hand);
    if (world.isClient()) {
      openClientScreen(hand, stack.copy());
    }
    return TypedActionResult.success(stack, world.isClient());
  }

  @Environment(EnvType.CLIENT)
  private static void openClientScreen(Hand hand, ItemStack snapshot) {
    BodyHealthScreen.open(hand, snapshot);
  }
}
