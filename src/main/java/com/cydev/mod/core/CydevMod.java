package com.cydev.mod.core;

import com.cydev.mod.command.CityCommand;
import com.cydev.mod.command.RoadCommand;
import com.cydev.mod.command.ZoneCommand;
import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.registry.ModBlocks;
import com.cydev.mod.registry.ModItemGroups;
import com.cydev.mod.registry.ModItems;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Ward;
import com.cydev.mod.world.item.WandSelections;
import com.cydev.mod.world.persistence.CityData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (server + client) entry point. */
public class CydevMod implements ModInitializer {
	public static final String MOD_ID = "cydev";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Elections run on real time, so checking every 5 seconds is plenty. */
	private static final int ELECTION_CHECK_TICKS = 100;

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		ModItemGroups.register();
		registerWandEvents();
		CityCommand.register();
		com.cydev.mod.command.GovCommand.register();
		ZoneCommand.register();
		RoadCommand.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % ELECTION_CHECK_TICKS != 0) {
				return;
			}
			CityData data = CityData.get(server);
			long now = System.currentTimeMillis();
			for (City city : data.registry().all().values()) {
				announce(server, data, ElectionService.tick(city, now));
				for (Ward ward : city.wards) {
					announce(server, data, ElectionService.tick(ward, now));
				}
			}
		});
		LOGGER.info("Cydev loaded - city simulation ready.");
	}

	/** Left-click with the Zoning Wand picks the first corner instead of breaking the block. */
	private static void registerWandEvents() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.isSpectator() || !player.getStackInHand(hand).isOf(ModItems.ZONING_WAND)) {
				return ActionResult.PASS;
			}
			if (player instanceof ServerPlayerEntity serverPlayer) {
				WandSelections.setCorner(serverPlayer, pos, true);
			}
			return ActionResult.FAIL;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			WandSelections.forget(handler.getPlayer().getUuid()));
	}

	private static void announce(MinecraftServer server, CityData data, String msg) {
		if (msg != null) {
			data.markDirty();
			server.getPlayerManager().broadcast(Text.literal(msg), false);
		}
	}
}
