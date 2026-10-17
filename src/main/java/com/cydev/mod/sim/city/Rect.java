package com.cydev.mod.sim.city;

import java.util.ArrayList;
import java.util.List;

/** Axis-aligned area on the x/z plane (inclusive block coordinates). */
public record Rect(int minX, int minZ, int maxX, int maxZ) {
	public static Rect of(int x1, int z1, int x2, int z2) {
		return new Rect(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
	}

	public boolean contains(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	public boolean containsRect(Rect o) {
		return o.minX >= minX && o.maxX <= maxX && o.minZ >= minZ && o.maxZ <= maxZ;
	}

	public boolean overlaps(Rect o) {
		return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	public long area() {
		return (long) (maxX - minX + 1) * (maxZ - minZ + 1);
	}

	/** This area with {@code cut} removed, as up to four rectangles. */
	public List<Rect> subtract(Rect cut) {
		List<Rect> out = new ArrayList<>();
		if (!overlaps(cut)) {
			out.add(this);
			return out;
		}
		if (cut.minZ > minZ) {
			out.add(new Rect(minX, minZ, maxX, cut.minZ - 1));
		}
		if (cut.maxZ < maxZ) {
			out.add(new Rect(minX, cut.maxZ + 1, maxX, maxZ));
		}
		int zLo = Math.max(minZ, cut.minZ);
		int zHi = Math.min(maxZ, cut.maxZ);
		if (cut.minX > minX) {
			out.add(new Rect(minX, zLo, cut.minX - 1, zHi));
		}
		if (cut.maxX < maxX) {
			out.add(new Rect(cut.maxX + 1, zLo, maxX, zHi));
		}
		return out;
	}
}
