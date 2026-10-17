package com.cydev.mod.network.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cydev.mod.sim.city.Rect;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoadNetworkTest {
	static RoadPos p(int x, int y, int z) {
		return new RoadPos(x, y, z);
	}

	/** A straight road along x from x0 to x1 (inclusive) at height y, row z. */
	static void line(RoadNetwork net, int x0, int x1, int y, int z) {
		for (int x = x0; x <= x1; x++) {
			net.add(p(x, y, z), RoadKind.ASPHALT);
		}
	}

	// ---- positions ----

	@Test
	void packedPositionsRoundTrip() {
		int[][] samples = {{0, 0, 0}, {1, 64, -1}, {-30000000, -64, 29999999}, {123456, 319, -98765}, {-1, -2048, -1}};
		for (int[] s : samples) {
			RoadPos pos = p(s[0], s[1], s[2]);
			assertEquals(pos, RoadPos.unpack(pos.pack()));
		}
	}

	// ---- building the network ----

	@Test
	void addRemoveAndKinds() {
		RoadNetwork net = new RoadNetwork();
		assertTrue(net.add(p(0, 64, 0), RoadKind.ASPHALT));
		assertFalse(net.add(p(0, 64, 0), RoadKind.ASPHALT), "same block again changes nothing");
		assertTrue(net.add(p(0, 64, 0), RoadKind.CENTER_LINE), "repainting changes the kind");
		assertEquals(RoadKind.CENTER_LINE, net.kindAt(p(0, 64, 0)));
		assertEquals(1, net.size());
		assertTrue(net.remove(p(0, 64, 0)));
		assertFalse(net.remove(p(0, 64, 0)));
		assertEquals(0, net.size());
	}

	@Test
	void kerbsAreNotDrivable() {
		RoadNetwork net = new RoadNetwork();
		net.add(p(0, 64, 0), RoadKind.ASPHALT);
		assertFalse(net.add(p(1, 64, 0), RoadKind.KERB));
		assertFalse(net.contains(p(1, 64, 0)));
		assertEquals(0, net.degree(p(0, 64, 0)));
		assertTrue(net.add(p(1, 64, 0), RoadKind.ASPHALT));
		assertTrue(net.add(p(1, 64, 0), RoadKind.KERB), "replacing a road block by a kerb removes it from the network");
		assertFalse(net.contains(p(1, 64, 0)));
	}

	// ---- connectivity ----

	@Test
	void sideBySideBlocksConnectButDiagonalsDoNot() {
		RoadNetwork net = new RoadNetwork();
		net.add(p(0, 64, 0), RoadKind.ASPHALT);
		net.add(p(1, 64, 0), RoadKind.ASPHALT);
		net.add(p(2, 64, 1), RoadKind.ASPHALT);
		assertTrue(net.connected(p(0, 64, 0), p(1, 64, 0)));
		assertFalse(net.connected(p(1, 64, 0), p(2, 64, 1)), "only touching at a corner");
		assertEquals(2, net.components().size());
	}

	@Test
	void slopesConnectUpToOneStep() {
		RoadNetwork net = new RoadNetwork();
		net.add(p(0, 64, 0), RoadKind.ASPHALT);
		net.add(p(1, 65, 0), RoadKind.ASPHALT);
		net.add(p(2, 67, 0), RoadKind.ASPHALT);
		assertTrue(net.connected(p(0, 64, 0), p(1, 65, 0)), "one block up is a slope");
		assertFalse(net.connected(p(1, 65, 0), p(2, 67, 0)), "two blocks up is a wall");
		assertTrue(net.connected(p(1, 65, 0), p(0, 64, 0)), "connections work both ways");
	}

	@Test
	void differentMarkingsStayConnected() {
		RoadNetwork net = new RoadNetwork();
		net.add(p(0, 64, 0), RoadKind.ASPHALT);
		net.add(p(1, 64, 0), RoadKind.CENTER_LINE);
		net.add(p(2, 64, 0), RoadKind.CROSSWALK);
		net.add(p(3, 64, 0), RoadKind.LANE_DASHED);
		net.add(p(4, 64, 0), RoadKind.EDGE_LINE);
		assertTrue(net.connected(p(0, 64, 0), p(4, 64, 0)));
		assertEquals(1, net.components().size());
	}

	@Test
	void removingABlockSplitsTheRoad() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 10, 64, 0);
		assertTrue(net.connected(p(0, 64, 0), p(10, 64, 0)));
		net.remove(p(5, 64, 0));
		assertFalse(net.connected(p(0, 64, 0), p(10, 64, 0)));
		assertEquals(2, net.components().size());
		net.add(p(5, 64, 0), RoadKind.ASPHALT);
		assertTrue(net.connected(p(0, 64, 0), p(10, 64, 0)), "repairing the gap reconnects it");
	}

	@Test
	void positionsWithoutRoadAreNeverConnected() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 3, 64, 0);
		assertFalse(net.connected(p(0, 64, 0), p(9, 64, 9)));
		assertFalse(net.connected(p(9, 64, 9), p(9, 64, 9)));
		assertTrue(net.componentOf(p(9, 64, 9)).isEmpty());
		assertNull(net.path(p(0, 64, 0), p(9, 64, 9)));
	}

	@Test
	void componentsAreSortedBiggestFirst() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 2, 64, 0);
		line(net, 0, 7, 64, 10);
		line(net, 0, 4, 64, 20);
		List<Set<RoadPos>> comps = net.components();
		assertEquals(List.of(8, 5, 3), comps.stream().map(Set::size).toList());
	}

	@Test
	void pathIsTheShortestRoute() {
		RoadNetwork net = new RoadNetwork();
		// A 3x3 ring of road with an extra tail.
		for (int i = 0; i < 3; i++) {
			net.add(p(i, 64, 0), RoadKind.ASPHALT);
			net.add(p(i, 64, 2), RoadKind.ASPHALT);
		}
		net.add(p(0, 64, 1), RoadKind.ASPHALT);
		net.add(p(2, 64, 1), RoadKind.ASPHALT);
		List<RoadPos> route = net.path(p(0, 64, 0), p(2, 64, 2));
		assertEquals(5, route.size());
		assertEquals(p(0, 64, 0), route.get(0));
		assertEquals(p(2, 64, 2), route.get(4));
		for (int i = 1; i < route.size(); i++) {
			assertTrue(net.neighbors(route.get(i - 1)).contains(route.get(i)), "route steps are neighbours");
		}
		assertNull(net.path(p(3, 64, 3), p(3, 64, 3)), "no road there, no route");
		assertEquals(List.of(p(0, 64, 0)), net.path(p(0, 64, 0), p(0, 64, 0)), "already there");
	}

	// ---- shape ----

	@Test
	void junctionsAndDeadEnds() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 4, 64, 2);
		net.add(p(2, 64, 1), RoadKind.ASPHALT);
		net.add(p(2, 64, 3), RoadKind.ASPHALT);
		assertTrue(net.isJunction(p(2, 64, 2)), "a crossroads");
		assertEquals(4, net.degree(p(2, 64, 2)));
		assertTrue(net.isDeadEnd(p(0, 64, 2)));
		assertFalse(net.isJunction(p(1, 64, 2)));
		RoadNetwork.Stats s = net.stats();
		assertEquals(7, s.tiles());
		assertEquals(1, s.components());
		assertEquals(7, s.largestComponent());
		assertEquals(1, s.junctions());
		assertEquals(4, s.deadEnds());
	}

	@Test
	void countsRoadBlocksInsideAnArea() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 9, 64, 0);
		assertEquals(5, net.countIn(Rect.of(0, 0, 4, 0)));
		assertEquals(0, net.countIn(Rect.of(0, 5, 9, 9)));
	}

	// ---- graph ----

	@Test
	void aStraightRoadIsOneEdge() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 9, 64, 0);
		RoadGraph g = net.graph();
		assertEquals(Set.of(p(0, 64, 0), p(9, 64, 0)), g.nodes());
		assertEquals(1, g.edges().size());
		RoadGraph.Edge e = g.edges().get(0);
		assertEquals(10, e.path().size());
		assertEquals(9, e.length());
	}

	@Test
	void aTJunctionHasThreeEdges() {
		RoadNetwork net = new RoadNetwork();
		line(net, 0, 6, 64, 0);
		for (int z = 1; z <= 4; z++) {
			net.add(p(3, 64, z), RoadKind.ASPHALT);
		}
		RoadGraph g = net.graph();
		assertEquals(Set.of(p(0, 64, 0), p(6, 64, 0), p(3, 64, 0), p(3, 64, 4)), g.nodes());
		assertEquals(3, g.edges().size());
		assertEquals(3, g.edgesAt(p(3, 64, 0)).size());
		int total = g.edges().stream().mapToInt(RoadGraph.Edge::length).sum();
		assertEquals(3 + 3 + 4, total);
	}

	@Test
	void aClosedLoopIsOneEdgeBackToItself() {
		RoadNetwork net = new RoadNetwork();
		for (int i = 0; i < 4; i++) {
			net.add(p(i, 64, 0), RoadKind.ASPHALT);
			net.add(p(i, 64, 3), RoadKind.ASPHALT);
		}
		net.add(p(0, 64, 1), RoadKind.ASPHALT);
		net.add(p(0, 64, 2), RoadKind.ASPHALT);
		net.add(p(3, 64, 1), RoadKind.ASPHALT);
		net.add(p(3, 64, 2), RoadKind.ASPHALT);
		assertEquals(12, net.size());
		RoadGraph g = net.graph();
		assertEquals(1, g.nodes().size(), "a ring has no junction, so one block anchors it");
		assertEquals(1, g.edges().size());
		RoadGraph.Edge e = g.edges().get(0);
		assertEquals(e.from(), e.to());
		assertEquals(12, e.length());
	}

	@Test
	void parallelLinksBetweenTwoJunctionsAreSeparateEdges() {
		RoadNetwork net = new RoadNetwork();
		// A rectangular ring (x 0..6, z 0..4) with a bar across the middle: two junctions, three links.
		line(net, 0, 6, 64, 0);
		line(net, 0, 6, 64, 4);
		for (int z = 1; z <= 3; z++) {
			net.add(p(0, 64, z), RoadKind.ASPHALT);
			net.add(p(6, 64, z), RoadKind.ASPHALT);
		}
		line(net, 1, 5, 64, 2);
		assertEquals(25, net.size());

		RoadGraph g = net.graph();
		assertEquals(Set.of(p(0, 64, 2), p(6, 64, 2)), g.nodes());
		assertEquals(List.of(6, 10, 10), g.edges().stream().map(RoadGraph.Edge::length).sorted().toList());
		assertEquals(3, g.edgesAt(p(0, 64, 2)).size());

		// Every block that is not a node lies on exactly one edge.
		Set<RoadPos> seen = new java.util.HashSet<>();
		for (RoadGraph.Edge e : g.edges()) {
			for (RoadPos q : e.path()) {
				if (!g.nodes().contains(q)) {
					assertTrue(seen.add(q), q + " is on two edges");
				}
			}
		}
		assertEquals(net.size() - g.nodes().size(), seen.size());
	}

	@Test
	void lonelyBlockIsANodeWithoutEdges() {
		RoadNetwork net = new RoadNetwork();
		net.add(p(5, 64, 5), RoadKind.ASPHALT);
		RoadGraph g = net.graph();
		assertEquals(Set.of(p(5, 64, 5)), g.nodes());
		assertTrue(g.edges().isEmpty());
	}
}
