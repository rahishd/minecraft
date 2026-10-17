package com.cydev.mod.sim.zone;

import com.cydev.mod.sim.city.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** All zoning of one city. Plots never overlap: painting a zone over another carves the old plot. */
public class ZoneMap {
	public final UUID cityId;
	public final List<ZonePlot> plots = new ArrayList<>();

	public ZoneMap(UUID cityId) {
		this.cityId = cityId;
	}

	public ZonePlot plotAt(int x, int z) {
		for (ZonePlot p : plots) {
			if (p.area().contains(x, z)) {
				return p;
			}
		}
		return null;
	}

	/** Zone type at a column, or null when the land is not zoned. */
	public ZoneType zoneAt(int x, int z) {
		ZonePlot p = plotAt(x, z);
		return p == null ? null : p.type();
	}

	/** Zoned blocks (x/z cells) of one type. */
	public long areaOf(ZoneType type) {
		long total = 0;
		for (ZonePlot p : plots) {
			if (p.type() == type) {
				total += p.area().area();
			}
		}
		return total;
	}

	/** True when every cell of the area already has exactly this zone type. */
	public boolean isFullyZoned(Rect area, ZoneType type) {
		return uncovered(area, type).isEmpty();
	}

	/** Parts of the area that are not yet zoned as {@code type}. */
	private List<Rect> uncovered(Rect area, ZoneType type) {
		List<Rect> remaining = new ArrayList<>();
		remaining.add(area);
		for (ZonePlot p : plots) {
			if (p.type() != type || !p.area().overlaps(area)) {
				continue;
			}
			List<Rect> next = new ArrayList<>();
			for (Rect r : remaining) {
				next.addAll(r.subtract(p.area()));
			}
			remaining = next;
		}
		return remaining;
	}

	/** Zones the area, replacing whatever zoning was there. Returns the new plot. */
	public ZonePlot paint(Rect area, ZoneType type, UUID by, long now) {
		carve(area);
		ZonePlot plot = new ZonePlot(UUID.randomUUID(), area, type, by, now);
		plots.add(plot);
		return plot;
	}

	/** Removes zoning inside the area. Returns how many zoned cells were removed. */
	public long clear(Rect area) {
		long removed = 0;
		for (ZonePlot p : carve(area)) {
			removed += p.area().area();
		}
		return removed;
	}

	/**
	 * Cuts the area out of every plot it touches. Returns the removed parts
	 * (already clipped to the area); the rest of each plot stays zoned.
	 */
	public List<ZonePlot> carve(Rect area) {
		List<ZonePlot> removed = new ArrayList<>();
		List<ZonePlot> kept = new ArrayList<>();
		for (ZonePlot p : plots) {
			if (!p.area().overlaps(area)) {
				kept.add(p);
				continue;
			}
			removed.add(p.withArea(intersection(p.area(), area)));
			for (Rect rest : p.area().subtract(area)) {
				kept.add(p.withArea(rest));
			}
		}
		plots.clear();
		plots.addAll(kept);
		return removed;
	}

	private static Rect intersection(Rect a, Rect b) {
		return new Rect(Math.max(a.minX(), b.minX()), Math.max(a.minZ(), b.minZ()),
			Math.min(a.maxX(), b.maxX()), Math.min(a.maxZ(), b.maxZ()));
	}
}
