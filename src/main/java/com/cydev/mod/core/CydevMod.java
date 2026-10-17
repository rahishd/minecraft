package com.cydev.mod.core;

import com.cydev.mod.command.CityCommand;
import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.world.persistence.CityData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.text.Text;
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
		CityCommand.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % ELECTION_CHECK_TICKS != 0) {
				return;
			}
			CityData data = CityData.get(server);
			long now = System.currentTimeMillis();
			for (City city : data.registry().all().values()) {
				String msg = ElectionService.tick(city, now);
				if (msg != null) {
					data.markDirty();
					server.getPlayerManager().broadcast(Text.literal(msg), false);
				}
			}
		});
		LOGGER.info("Cydev loaded - city simulation ready.");
	}
}
