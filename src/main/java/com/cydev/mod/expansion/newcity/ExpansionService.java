package com.cydev.mod.expansion.newcity;

import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRegistry;
import com.cydev.mod.sim.city.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Land claims and city expansion. A Mayor can carve part of their city's land
 * into a new city; the new city holds its own election, while the original
 * Mayor keeps the original city.
 */
public final class ExpansionService {
	private ExpansionService() {
	}

	public static City cityAt(CityRegistry registry, int x, int z) {
		for (City c : registry.all().values()) {
			for (Rect r : c.regions) {
				if (r.contains(x, z)) {
					return c;
				}
			}
		}
		return null;
	}

	/** Claims unclaimed land for a city (operator / founding). Returns an error or null. */
	public static String claim(CityRegistry registry, City city, Rect area) {
		for (City other : registry.all().values()) {
			for (Rect r : other.regions) {
				if (r.overlaps(area)) {
					return "That area overlaps land belonging to " + other.name + ".";
				}
			}
		}
		city.regions.add(area);
		return null;
	}

	/**
	 * The parent city's Mayor splits an area off into a new city and an
	 * election for its first mayor starts. Returns an error message, or null.
	 */
	public static String expand(CityRegistry registry, City parent, UUID actor, String newName, Rect area,
								long now, City[] createdOut) {
		if (!parent.isMayor(actor)) {
			return "Only the Mayor of " + parent.name + " can create a new city from it.";
		}
		for (City c : registry.all().values()) {
			if (c.name.equalsIgnoreCase(newName)) {
				return "A city named " + newName + " already exists.";
			}
		}
		Rect source = null;
		for (Rect r : parent.regions) {
			if (r.containsRect(area)) {
				source = r;
			}
		}
		if (source == null) {
			return "The selected area must lie inside " + parent.name + "'s land.";
		}
		List<Rect> remaining = new ArrayList<>(parent.regions);
		remaining.remove(source);
		remaining.addAll(source.subtract(area));
		if (remaining.isEmpty()) {
			return parent.name + " would have no land left.";
		}
		parent.regions.clear();
		parent.regions.addAll(remaining);

		City created = registry.create(newName);
		created.parentId = parent.id;
		created.regions.add(area);
		ElectionService.start(created, now, false);
		if (createdOut != null && createdOut.length > 0) {
			createdOut[0] = created;
		}
		return null;
	}
}
