package com.cydev.mod.network.road;

import java.util.ArrayList;
import java.util.List;

/** Position of one road block. Plain ints, so the network stays free of Minecraft types. */
public record RoadPos(int x, int y, int z) implements Comparable<RoadPos> {
	/** Tallest step between neighbouring road blocks (a slope or stairs). */
	public static final int MAX_STEP = 1;

	private static final int XZ_BITS = 26;
	private static final int Y_BITS = 12;

	/** Same layout as Minecraft's BlockPos.asLong, so the world adapter can use either. */
	public long pack() {
		long l = 0;
		l |= ((long) x & ((1L << XZ_BITS) - 1)) << (Y_BITS + XZ_BITS);
		l |= (long) y & ((1L << Y_BITS) - 1);
		l |= ((long) z & ((1L << XZ_BITS) - 1)) << Y_BITS;
		return l;
	}

	public static RoadPos unpack(long l) {
		int x = (int) (l >> (Y_BITS + XZ_BITS));
		int y = (int) (l << (64 - Y_BITS) >> (64 - Y_BITS));
		int z = (int) (l << (64 - XZ_BITS - Y_BITS) >> (64 - XZ_BITS));
		return new RoadPos(x, y, z);
	}

	/** Candidate neighbours: the four horizontal sides, each at the same height or one step up/down. */
	List<RoadPos> candidates() {
		List<RoadPos> out = new ArrayList<>(12);
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] s : sides) {
			for (int dy = -MAX_STEP; dy <= MAX_STEP; dy++) {
				out.add(new RoadPos(x + s[0], y + dy, z + s[1]));
			}
		}
		return out;
	}

	@Override
	public int compareTo(RoadPos o) {
		int c = Integer.compare(x, o.x);
		if (c == 0) {
			c = Integer.compare(y, o.y);
		}
		return c != 0 ? c : Integer.compare(z, o.z);
	}
}
