package net.imsanty.moodles;

import net.fabricmc.api.ModInitializer;
import net.imsanty.moodles.block.ModBlocks;
import net.imsanty.moodles.item.ModItemGroups;
import net.imsanty.moodles.item.ModItems;
import net.imsanty.moodles.moodle.ModMoodles;
import net.imsanty.moodles.moodle.debug.MoodlesDebug;
import net.imsanty.moodles.moodle.network.ApplyBandagePayload;
import net.imsanty.moodles.moodle.network.SyncBodyHealthPayload;
import net.imsanty.moodles.moodle.network.SyncMoodlesPayload;
import net.imsanty.moodles.moodle.server.MoodlesServerEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Moodles implements ModInitializer {
	public static final String MOD_ID = "moodles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SyncMoodlesPayload.register();
		SyncBodyHealthPayload.register();
		ApplyBandagePayload.register();
		ModItemGroups.registerItemGroups();

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModMoodles.bootstrap();
		MoodlesServerEvents.register();
		MoodlesDebug.register();
	}
}