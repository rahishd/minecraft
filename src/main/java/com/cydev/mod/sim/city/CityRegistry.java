package com.cydev.mod.sim.city;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Holds every city in the world (later: persisted with the world). */
public class CityRegistry {
	private final Map<UUID, City> cities = new LinkedHashMap<>();

	public City create(String name) {
		City city = new City(UUID.randomUUID(), name);
		cities.put(city.id, city);
		return city;
	}

	public Map<UUID, City> all() {
		return cities;
	}
}
