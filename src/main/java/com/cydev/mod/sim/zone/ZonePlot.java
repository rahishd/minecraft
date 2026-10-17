package com.cydev.mod.sim.zone;

import com.cydev.mod.sim.city.Rect;
import java.util.UUID;

/** A rectangle of city land assigned to one zone type. Plots of a city never overlap. */
public record ZonePlot(UUID id, Rect area, ZoneType type, UUID zonedBy, long zonedAt) {
	public ZonePlot withArea(Rect newArea) {
		return new ZonePlot(UUID.randomUUID(), newArea, type, zonedBy, zonedAt);
	}
}
