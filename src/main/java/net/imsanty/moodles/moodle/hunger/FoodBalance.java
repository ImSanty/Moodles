package net.imsanty.moodles.moodle.hunger;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

final class FoodBalance {
  private static final Map<Item, FoodGroup> GROUPS = new IdentityHashMap<>();

  private FoodBalance() {
  }

  static float computeGain(ItemStack stack, FoodComponent food, float nutritionMultiplier, float saturationMultiplier) {
    float base = (food.nutrition() * nutritionMultiplier) + (food.saturation() * saturationMultiplier);
    FoodGroup group = GROUPS.get(stack.getItem());
    if (group == null) {
      return Math.max(0.0f, base);
    }
    float adjusted = (base * group.multiplier) + group.flatBonus;
    return MathHelper.clamp(adjusted, 0.0f, 48.0f);
  }

  private static void register(FoodGroup group, Item... items) {
    for (Item item : items) {
      GROUPS.put(item, group);
    }
  }

  private enum FoodGroup {
    RAW_MEAT(0.45f, -3.0f),
    RAW_FISH(0.5f, -2.0f),
    COOKED_MEAT(1.75f, 6.0f),
    COOKED_FISH(1.55f, 3.5f),
    VEGETABLE(0.9f, 1.5f),
    FRUIT(0.75f, 0.0f),
    SWEET(0.6f, 0.0f),
    BREADS(1.25f, 2.0f),
    STEW(1.8f, 8.0f),
    GOLDEN(2.4f, 16.0f),
    DRIED(0.55f, -1.0f),
    DELICACY(1.4f, 4.0f);

    final float multiplier;
    final float flatBonus;

    FoodGroup(float multiplier, float flatBonus) {
      this.multiplier = multiplier;
      this.flatBonus = flatBonus;
    }
  }

  static {
    register(FoodGroup.RAW_MEAT, Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT);
    register(FoodGroup.RAW_FISH, Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH);
    register(FoodGroup.COOKED_MEAT, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
        Items.COOKED_RABBIT);
    register(FoodGroup.COOKED_FISH, Items.COOKED_COD, Items.COOKED_SALMON);
    register(FoodGroup.BREADS, Items.BREAD, Items.BAKED_POTATO);
    register(FoodGroup.VEGETABLE, Items.CARROT, Items.POTATO, Items.BEETROOT);
    register(FoodGroup.FRUIT, Items.APPLE, Items.CHORUS_FRUIT, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
        Items.MELON_SLICE);
    register(FoodGroup.SWEET, Items.COOKIE, Items.HONEY_BOTTLE);
    register(FoodGroup.STEW, Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.SUSPICIOUS_STEW, Items.BEETROOT_SOUP);
    register(FoodGroup.GOLDEN, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT);
    register(FoodGroup.DRIED, Items.DRIED_KELP, Items.ROTTEN_FLESH);
    register(FoodGroup.DELICACY, Items.CAKE, Items.PUMPKIN_PIE);
  }
}
