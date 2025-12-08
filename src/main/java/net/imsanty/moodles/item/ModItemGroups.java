package net.imsanty.moodles.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.imsanty.moodles.Moodles;
import net.imsanty.moodles.block.ModBlocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

  public static final ItemGroup MOODLES_ITEM_GROUP = Registry.register(Registries.ITEM_GROUP,
      Identifier.of(Moodles.MOD_ID, "moodles_item_group"),
      FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.BANDAGE))
          .displayName(Text.translatable("itemgroup.moodles.moodles_item_group"))
          .entries((displayContext, entries) -> {
            entries.add(ModItems.BANDAGE);
            entries.add(ModItems.RAW_PINK_GARNET);
          }).build());

  public static final ItemGroup MOODLES_BLOCK_GROUP = Registry.register(Registries.ITEM_GROUP,
      Identifier.of(Moodles.MOD_ID, "moodles_block_group"),
      FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.PINK_GARNET_BLOCK))
          .displayName(Text.translatable("itemgroup.moodles.moodles_block_group"))
          .entries((displayContext, entries) -> {
            entries.add(ModBlocks.PINK_GARNET_BLOCK);
            entries.add(ModBlocks.RAW_PINK_GARNET_BLOCK);
          }).build());

  public static void registerItemGroups() {
    // Registration logic for mod item groups
    Moodles.LOGGER.info("Registering Mod Item Groups for " + Moodles.MOD_ID);
  }
}
