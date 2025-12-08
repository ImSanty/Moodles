package net.imsanty.moodles.block;

import net.imsanty.moodles.Moodles;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {
  public static final Block PINK_GARNET_BLOCK = registerBlock("pink_garnet_block",
      new Block(AbstractBlock.Settings.create().strength(4.0f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK)));
  public static final Block RAW_PINK_GARNET_BLOCK = registerBlock("raw_pink_garnet_block",
      new Block(AbstractBlock.Settings.create().strength(3.0f).requiresTool().sounds(BlockSoundGroup.STONE)));

  // Block registration logic
  private static Block registerBlock(String name, Block block) {
    registerBlockItem(name, block);
    return Registry.register(Registries.BLOCK, Identifier.of(Moodles.MOD_ID, name), block);
  }

  private static void registerBlockItem(String name, Block block) {
    Registry.register(Registries.ITEM, Identifier.of(Moodles.MOD_ID, name),
        new BlockItem(block, new Item.Settings()));
  }

  public static void registerModBlocks() {
    // Registration logic for mod blocks
    Moodles.LOGGER.info("Registering Mod Blocks for " + Moodles.MOD_ID);
  }

}
