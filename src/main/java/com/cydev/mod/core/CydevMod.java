package com.cydev.mod.core;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (server + client) entry point. */
public class CydevMod implements ModInitializer {
	public static final String MOD_ID = "cydev";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Cydev loaded - city simulation ready.");
	}
}
