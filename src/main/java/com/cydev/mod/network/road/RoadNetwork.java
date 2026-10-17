package com.cydev.mod.network.road;

import com.cydev.mod.sim.city.Rect;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every drivable road block in the world. Two blocks are connected when they
 * touch on a side at the same height or one step apart (slopes). Connectivity
 * is derived from the block set, so adding or removing a block can never leave
 * the network out of date.
 */
public class RoadNetwork {
	/** Summary used by the road commands and later by the traffic and service systems. */
	public record Stats(int tiles, int components, int largestComponent, int junctions, int deadEnds) {
	}

	private final Map<RoadPos, RoadKind> tiles = new HashMap<>();

	/**
	 * Adds a road block, or changes its kind. Non-drivable kinds (kerbs) are not
	 * part of the network and remove any tile at that position. Returns true if
	 * the network changed.
	 */
	public boolean add(RoadPos pos, RoadKind kind) {
		if (!kind.drivable()) {
			return remove(pos);
		}
		return tiles.put(pos, kind) != kind;
	}

	/** Returns true if there was a road block at the position. */
	public boolean remove(RoadPos pos) {
		return tiles.remove(pos) != null;
	}

	public boolean contains(RoadPos pos) {
		return tiles.containsKey(pos);
	}

	public RoadKind kindAt(RoadPos pos) {
		return tiles.get(pos);
	}

	public int size() {
		return tiles.size();
	}

	public Map<RoadPos, RoadKind> tiles() {
		return Collections.unmodifiableMap(tiles);
	}

	/** Road blocks whose x/z lie inside the area (for example a city's land). */
	public int countIn(Rect area) {
		int n = 0;
		for (RoadPos p : tiles.keySet()) {
			if (area.contains(p.x(), p.z())) {
				n++;
			}
		}
		return n;
	}

	public List<RoadPos> neighbors(RoadPos pos) {
		List<RoadPos> out = new ArrayList<>(4);
		for (RoadPos c : pos.candidates()) {
			if (tiles.containsKey(c)) {
				out.add(c);
			}
		}
		return out;
	}

	public int degree(RoadPos pos) {
		return neighbors(pos).size();
	}

	/** Three or more roads meet here. */
	public boolean isJunction(RoadPos pos) {
		return contains(pos) && degree(pos) >= 3;
	}

	/** A road that ends here (exactly one neighbour). */
	public boolean isDeadEnd(RoadPos pos) {
		return contains(pos) && degree(pos) == 1;
	}

	/** Every road block reachable from the position, including itself; empty if there is no road there. */
	public Set<RoadPos> componentOf(RoadPos start) {
		Set<RoadPos> seen = new HashSet<>();
		if (!tiles.containsKey(start)) {
			return seen;
		}
		Deque<RoadPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty()) {
			for (RoadPos n : neighbors(queue.poll())) {
				if (seen.add(n)) {
					queue.add(n);
				}
			}
		}
		return seen;
	}

	/** The separate road systems, biggest first. */
	public List<Set<RoadPos>> components() {
		List<Set<RoadPos>> out = new ArrayList<>();
		Set<RoadPos> visited = new HashSet<>();
		for (RoadPos p : tiles.keySet()) {
			if (!visited.contains(p)) {
				Set<RoadPos> c = componentOf(p);
				visited.addAll(c);
				out.add(c);
			}
		}
		out.sort((a, b) -> Integer.compare(b.size(), a.size()));
		return out;
	}

	/** True when both positions are road blocks of the same connected road system. */
	public boolean connected(RoadPos a, RoadPos b) {
		return path(a, b) != null;
	}

	/** Fewest-blocks route from a to b including both ends, or null if they are not connected. */
	public List<RoadPos> path(RoadPos from, RoadPos to) {
		if (!tiles.containsKey(from) || !tiles.containsKey(to)) {
			return null;
		}
		Map<RoadPos, RoadPos> parent = new HashMap<>();
		Deque<RoadPos> queue = new ArrayDeque<>();
		queue.add(from);
		parent.put(from, from);
		while (!queue.isEmpty() && !parent.containsKey(to)) {
			RoadPos cur = queue.poll();
			for (RoadPos n : neighbors(cur)) {
				if (parent.putIfAbsent(n, cur) == null) {
					queue.add(n);
				}
			}
		}
		if (!parent.containsKey(to)) {
			return null;
		}
		List<RoadPos> route = new ArrayList<>();
		for (RoadPos p = to; ; p = parent.get(p)) {
			route.add(p);
			if (p.equals(from)) {
				break;
			}
		}
		Collections.reverse(route);
		return route;
	}

	public Stats stats() {
		List<Set<RoadPos>> comps = components();
		int junctions = 0;
		int deadEnds = 0;
		for (RoadPos p : tiles.keySet()) {
			int d = degree(p);
			if (d >= 3) {
				junctions++;
			} else if (d == 1) {
				deadEnds++;
			}
		}
		return new Stats(tiles.size(), comps.size(), comps.isEmpty() ? 0 : comps.get(0).size(), junctions, deadEnds);
	}

	/** The network as junctions and road links, for routing and traffic. */
	public RoadGraph graph() {
		return RoadGraph.build(this);
	}
}
