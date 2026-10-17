package com.cydev.mod.sim.zone;

import com.cydev.mod.sim.city.Rect;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Zoning of every city in the world, one {@link ZoneMap} per city. */
public class ZoneRegistry {
	private final Map<UUID, ZoneMap> maps = new LinkedHashMap<>();

	public ZoneMap of(UUID cityId) {
		return maps.computeIfAbsent(cityId, ZoneMap::new);
	}

	public Map<UUID, ZoneMap> all() {
		return maps;
	}

	/**
	 * Moves the zoning inside {@code area} from one city to another, used when
	 * a Mayor carves a new city out of their land.
	 */
	public void transfer(UUID fromCity, UUID toCity, Rect area) {
		ZoneMap from = maps.get(fromCity);
		if (from == null) {
			return;
		}
		ZoneMap to = of(toCity);
		to.plots.addAll(from.carve(area));
	}
}
