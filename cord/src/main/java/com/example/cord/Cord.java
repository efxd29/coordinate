package com.example.cord;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cord implements ModInitializer {
	public static final String MOD_ID = "cord";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Method ini jalan pas game lagi mulai (server + client),
		// pas mod-mod dan sistem-sistem penting udah siap dipake.
		LOGGER.info("Cord nyala, bro!");
	}
}
