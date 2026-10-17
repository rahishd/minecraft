package com.cydev.mod.network.road;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A road network reduced to nodes (junctions, dead ends, lone blocks) and
 * edges (the stretches of single-lane-wide road between them). A snapshot:
 * build a new one after the network changes.
 */
public final class RoadGraph {
	/** A stretch of road between two nodes. {@code path} runs from {@code from} to {@code to} inclusive. */
	public record Edge(RoadPos from, RoadPos to, List<RoadPos> path) {
		/** Number of block steps along the edge. */
		public int length() {
			return path.size() - 1;
		}
	}

	private record HalfEdge(RoadPos node, RoadPos next) {
	}

	private final Set<RoadPos> nodes;
	private final List<Edge> edges;

	private RoadGraph(Set<RoadPos> nodes, List<Edge> edges) {
		this.nodes = nodes;
		this.edges = edges;
	}

	public Set<RoadPos> nodes() {
		return nodes;
	}

	public List<Edge> edges() {
		return edges;
	}

	public List<Edge> edgesAt(RoadPos node) {
		List<Edge> out = new ArrayList<>();
		for (Edge e : edges) {
			if (e.from().equals(node) || e.to().equals(node)) {
				out.add(e);
			}
		}
		return out;
	}

	static RoadGraph build(RoadNetwork net) {
		Set<RoadPos> nodes = new LinkedHashSet<>();
		for (RoadPos p : net.tiles().keySet()) {
			if (net.degree(p) != 2) {
				nodes.add(p);
			}
		}
		// A closed loop of plain road has no junction; pick one block to anchor it.
		for (Set<RoadPos> comp : net.components()) {
			boolean anchored = false;
			RoadPos lowest = null;
			for (RoadPos p : comp) {
				anchored |= nodes.contains(p);
				if (lowest == null || p.compareTo(lowest) < 0) {
					lowest = p;
				}
			}
			if (!anchored) {
				nodes.add(lowest);
			}
		}

		List<Edge> edges = new ArrayList<>();
		Set<HalfEdge> walked = new HashSet<>();
		for (RoadPos node : nodes) {
			for (RoadPos first : net.neighbors(node)) {
				if (walked.contains(new HalfEdge(node, first))) {
					continue;
				}
				List<RoadPos> path = new ArrayList<>();
				path.add(node);
				RoadPos prev = node;
				RoadPos cur = first;
				while (!nodes.contains(cur)) {
					path.add(cur);
					RoadPos next = null;
					for (RoadPos n : net.neighbors(cur)) {
						if (!n.equals(prev)) {
							next = n;
						}
					}
					prev = cur;
					cur = next;
				}
				path.add(cur);
				walked.add(new HalfEdge(node, first));
				walked.add(new HalfEdge(cur, prev));
				edges.add(new Edge(node, cur, path));
			}
		}
		return new RoadGraph(nodes, edges);
	}
}
