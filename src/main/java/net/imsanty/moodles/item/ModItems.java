package net.imsanty.moodles.item;

import net.imsanty.moodles.Moodles;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

  public static final Item BANDAGE = registerItem("bandage",
      new BandageItem(new Item.Settings().maxCount(64)));
  public static final Item RAW_PINK_GARNET = registerItem("raw_pink_garnet",
      new Item(new Item.Settings()));

  private static Item registerItem(String name, Item item) {
    // Item registration logic
    return Registry.register(Registries.ITEM, Identifier.of(Moodles.MOD_ID, name), item);
  }

  public static void registerModItems() {
    // Registration logic for mod items
    Moodles.LOGGER.info("Registering Mod Items for " + Moodles.MOD_ID);
  }

}
