package com.cydev.mod.sim.zone;

import com.cydev.mod.expansion.newcity.ExpansionService;
import com.cydev.mod.government.council.ActorResolver;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRegistry;
import com.cydev.mod.sim.city.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Zoning rules: land can only be zoned inside a city's own land, and only by
 * that city's Mayor or one of its Ward Chairpersons. Methods return an error
 * message, or null on success.
 */
public final class ZoningService {
	/** Longest side of one zoning selection, so a single click cannot repaint a whole city. */
	public static final int MAX_SIDE = 96;

	private ZoningService() {
	}

	/** True when the player is the city's Mayor or the Chairperson of one of its wards. */
	public static boolean canZone(City city, UUID playerId) {
		for (Actor a : ActorResolver.rolesOf(city, playerId, "", null)) {
			if (a.position() == Position.MAYOR || a.position() == Position.WARD_CHAIR) {
				return true;
			}
		}
		return false;
	}

	/** True when every block column of the area lies inside the city's land. */
	public static boolean isInsideCityLand(City city, Rect area) {
		List<Rect> remaining = new ArrayList<>();
		remaining.add(area);
		for (Rect land : city.regions) {
			List<Rect> next = new ArrayList<>();
			for (Rect r : remaining) {
				next.addAll(r.subtract(land));
			}
			remaining = next;
			if (remaining.isEmpty()) {
				return true;
			}
		}
		return remaining.isEmpty();
	}

	/**
	 * Zones the area for the player. The city is the one that owns the land at
	 * the area's corner. {@code plotOut[0]} receives the new plot.
	 */
	public static String zone(CityRegistry cities, ZoneRegistry zones, UUID playerId, Rect area, ZoneType type,
							  long now, ZonePlot[] plotOut) {
		City[] cityOut = new City[1];
		String err = check(cities, playerId, area, cityOut);
		if (err != null) {
			return err;
		}
		ZoneMap map = zones.of(cityOut[0].id);
		if (map.isFullyZoned(area, type)) {
			return "That area is already zoned " + type.label() + ".";
		}
		ZonePlot plot = map.paint(area, type, playerId, now);
		if (plotOut != null && plotOut.length > 0) {
			plotOut[0] = plot;
		}
		return null;
	}

	/** Removes zoning inside the area (same authority rules as zoning). */
	public static String clear(CityRegistry cities, ZoneRegistry zones, UUID playerId, Rect area) {
		City[] cityOut = new City[1];
		String err = check(cities, playerId, area, cityOut);
		if (err != null) {
			return err;
		}
		if (zones.of(cityOut[0].id).clear(area) == 0) {
			return "Nothing is zoned in that area.";
		}
		return null;
	}

	private static String check(CityRegistry cities, UUID playerId, Rect area, City[] cityOut) {
		if (area.maxX() - area.minX() + 1 > MAX_SIDE || area.maxZ() - area.minZ() + 1 > MAX_SIDE) {
			return "A zoning selection can be at most " + MAX_SIDE + " x " + MAX_SIDE + " blocks.";
		}
		City city = ExpansionService.cityAt(cities, area.minX(), area.minZ());
		if (city == null) {
			return "That land does not belong to any city.";
		}
		if (!canZone(city, playerId)) {
			return "Only the Mayor or a Ward Chairperson of " + city.name + " can zone its land.";
		}
		if (!isInsideCityLand(city, area)) {
			return "The whole selection must lie inside " + city.name + "'s land.";
		}
		cityOut[0] = city;
		return null;
	}
}
