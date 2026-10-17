package com.cydev.mod.sim.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cydev.mod.expansion.newcity.ExpansionService;
import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRegistry;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.city.Ward;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZoningTest {
	static final long T0 = 5_000_000L;

	CityRegistry registry;
	ZoneRegistry zones;
	City abc;
	UUID mayor;
	UUID chair;

	@BeforeEach
	void setUp() {
		registry = new CityRegistry();
		zones = new ZoneRegistry();
		abc = registry.create("ABC");
		mayor = UUID.randomUUID();
		abc.mayorId = mayor;
		abc.mayorName = "Mayor";
		Ward ward = new Ward(UUID.randomUUID(), 1, "One");
		chair = UUID.randomUUID();
		ward.chairId = chair;
		abc.wards.add(ward);
		assertNull(ExpansionService.claim(registry, abc, Rect.of(0, 0, 99, 99)));
	}

	// ---- who and where ----

	@Test
	void mayorCanZoneInsideTheirCity() {
		ZonePlot[] out = new ZonePlot[1];
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(10, 10, 29, 24), ZoneType.RESIDENTIAL, T0, out));
		assertNotNull(out[0]);
		assertEquals(ZoneType.RESIDENTIAL, out[0].type());
		assertEquals(mayor, out[0].zonedBy());
		assertEquals(ZoneType.RESIDENTIAL, zones.of(abc.id).zoneAt(15, 20));
		assertNull(zones.of(abc.id).zoneAt(30, 20), "just outside the selection");
		assertEquals(20 * 15, zones.of(abc.id).areaOf(ZoneType.RESIDENTIAL));
	}

	@Test
	void wardChairCanZone() {
		assertNull(ZoningService.zone(registry, zones, chair, Rect.of(0, 0, 9, 9), ZoneType.COMMERCIAL, T0, null));
		assertEquals(ZoneType.COMMERCIAL, zones.of(abc.id).zoneAt(5, 5));
	}

	@Test
	void othersCannotZone() {
		Rect area = Rect.of(0, 0, 9, 9);
		UUID resident = UUID.randomUUID();
		assertNotNull(ZoningService.zone(registry, zones, resident, area, ZoneType.OFFICE, T0, null));

		UUID deputy = UUID.randomUUID();
		abc.deputyId = deputy;
		assertNotNull(ZoningService.zone(registry, zones, deputy, area, ZoneType.OFFICE, T0, null),
			"the Deputy Mayor is not a zoning authority");

		UUID engineer = UUID.randomUUID();
		abc.appointments.add(new Appointment(engineer, "Eng", Position.ENGINEER));
		assertNotNull(ZoningService.zone(registry, zones, engineer, area, ZoneType.OFFICE, T0, null),
			"appointed staff cannot zone");
		assertTrue(zones.of(abc.id).plots.isEmpty());
	}

	@Test
	void mayorOfAnotherCityCannotZoneHere() {
		City xyz = registry.create("XYZ");
		UUID otherMayor = UUID.randomUUID();
		xyz.mayorId = otherMayor;
		assertNull(ExpansionService.claim(registry, xyz, Rect.of(200, 0, 299, 99)));
		String err = ZoningService.zone(registry, zones, otherMayor, Rect.of(0, 0, 9, 9), ZoneType.OFFICE, T0, null);
		assertNotNull(err);
		assertTrue(err.contains("ABC"), err);
	}

	@Test
	void unclaimedLandCannotBeZoned() {
		assertNotNull(ZoningService.zone(registry, zones, mayor, Rect.of(500, 500, 510, 510), ZoneType.INDUSTRIAL, T0, null));
		assertTrue(zones.of(abc.id).plots.isEmpty());
	}

	@Test
	void selectionMustStayInsideTheCity() {
		assertNotNull(ZoningService.zone(registry, zones, mayor, Rect.of(90, 90, 110, 110), ZoneType.RESIDENTIAL, T0, null),
			"sticks out past the city border");
		assertNotNull(ZoningService.zone(registry, zones, mayor, Rect.of(-5, 10, 10, 20), ZoneType.RESIDENTIAL, T0, null),
			"starts outside the city");
		assertTrue(zones.of(abc.id).plots.isEmpty());
	}

	@Test
	void selectionMayCoverSeveralAdjacentPiecesOfTheSameCity() {
		// ABC also owns the strip next to its first block of land.
		assertNull(ExpansionService.claim(registry, abc, Rect.of(100, 0, 149, 99)));
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(80, 10, 120, 30), ZoneType.COMMERCIAL, T0, null));
		assertEquals(ZoneType.COMMERCIAL, zones.of(abc.id).zoneAt(110, 20));
	}

	@Test
	void selectionCannotCrossIntoAnotherCity() {
		City xyz = registry.create("XYZ");
		xyz.mayorId = UUID.randomUUID();
		assertNull(ExpansionService.claim(registry, xyz, Rect.of(100, 0, 199, 99)));
		assertNotNull(ZoningService.zone(registry, zones, mayor, Rect.of(80, 10, 120, 30), ZoneType.COMMERCIAL, T0, null));
		assertTrue(zones.of(abc.id).plots.isEmpty());
	}

	@Test
	void selectionSizeIsLimited() {
		City big = registry.create("Big");
		UUID bigMayor = UUID.randomUUID();
		big.mayorId = bigMayor;
		assertNull(ExpansionService.claim(registry, big, Rect.of(1000, 1000, 1999, 1999)));
		int side = ZoningService.MAX_SIDE;
		assertNull(ZoningService.zone(registry, zones, bigMayor, Rect.of(1000, 1000, 1000 + side - 1, 1000 + side - 1),
			ZoneType.RESIDENTIAL, T0, null));
		assertNotNull(ZoningService.zone(registry, zones, bigMayor, Rect.of(1000, 1000, 1000 + side, 1010),
			ZoneType.RESIDENTIAL, T0, null));
	}

	// ---- repainting ----

	@Test
	void zoningAgainOverwritesAndKeepsTheRest() {
		ZoneMap map = zones.of(abc.id);
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(0, 0, 19, 19), ZoneType.RESIDENTIAL, T0, null));
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(10, 10, 29, 29), ZoneType.COMMERCIAL, T0, null));

		assertEquals(ZoneType.RESIDENTIAL, map.zoneAt(5, 5));
		assertEquals(ZoneType.COMMERCIAL, map.zoneAt(15, 15), "overlap goes to the newer zone");
		assertEquals(ZoneType.COMMERCIAL, map.zoneAt(25, 25));
		assertEquals(400 - 100, map.areaOf(ZoneType.RESIDENTIAL));
		assertEquals(400, map.areaOf(ZoneType.COMMERCIAL));
	}

	@Test
	void zoningTheSameThingTwiceIsRejected() {
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(0, 0, 19, 19), ZoneType.OFFICE, T0, null));
		assertNotNull(ZoningService.zone(registry, zones, mayor, Rect.of(5, 5, 10, 10), ZoneType.OFFICE, T0, null));
		assertEquals(1, zones.of(abc.id).plots.size());
	}

	@Test
	void plotsNeverOverlapAndMatchACellByCellModel() {
		Random rnd = new Random(42);
		ZoneType[] grid = new ZoneType[100 * 100];
		ZoneType[] types = ZoneType.values();
		for (int i = 0; i < 200; i++) {
			int x1 = rnd.nextInt(100);
			int z1 = rnd.nextInt(100);
			Rect r = Rect.of(x1, z1, Math.min(99, x1 + rnd.nextInt(30)), Math.min(99, z1 + rnd.nextInt(30)));
			ZoneType t = types[rnd.nextInt(types.length)];
			String err = ZoningService.zone(registry, zones, mayor, r, t, T0, null);
			if (err == null) {
				for (int x = r.minX(); x <= r.maxX(); x++) {
					for (int z = r.minZ(); z <= r.maxZ(); z++) {
						grid[z * 100 + x] = t;
					}
				}
			}
		}
		ZoneMap map = zones.of(abc.id);
		for (int x = 0; x < 100; x++) {
			for (int z = 0; z < 100; z++) {
				assertEquals(grid[z * 100 + x], map.zoneAt(x, z), "cell " + x + "," + z);
				int covering = 0;
				for (ZonePlot p : map.plots) {
					if (p.area().contains(x, z)) {
						covering++;
					}
				}
				assertTrue(covering <= 1, "cell " + x + "," + z + " is in " + covering + " plots");
			}
		}
	}

	// ---- clearing ----

	@Test
	void clearingRemovesOnlyTheSelectedPart() {
		ZoneMap map = zones.of(abc.id);
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(0, 0, 19, 19), ZoneType.INDUSTRIAL, T0, null));
		assertNull(ZoningService.clear(registry, zones, mayor, Rect.of(5, 5, 14, 14)));
		assertNull(map.zoneAt(10, 10));
		assertEquals(ZoneType.INDUSTRIAL, map.zoneAt(2, 2));
		assertEquals(400 - 100, map.areaOf(ZoneType.INDUSTRIAL));
		assertNotNull(ZoningService.clear(registry, zones, mayor, Rect.of(5, 5, 14, 14)), "nothing left to clear");
		assertNotNull(ZoningService.clear(registry, zones, UUID.randomUUID(), Rect.of(0, 0, 4, 4)),
			"a resident cannot clear zoning");
	}

	// ---- expansion ----

	@Test
	void zoningMovesWithLandCarvedIntoANewCity() {
		assertNull(ZoningService.zone(registry, zones, mayor, Rect.of(0, 0, 59, 59), ZoneType.RESIDENTIAL, T0, null));
		City[] created = new City[1];
		Rect carved = Rect.of(40, 40, 99, 99);
		assertNull(ExpansionService.expand(registry, abc, mayor, "XYZ", carved, T0, created));

		zones.transfer(abc.id, created[0].id, carved);

		assertEquals(ZoneType.RESIDENTIAL, zones.of(abc.id).zoneAt(10, 10));
		assertNull(zones.of(abc.id).zoneAt(50, 50), "no longer ABC's");
		assertEquals(ZoneType.RESIDENTIAL, zones.of(created[0].id).zoneAt(50, 50));
		assertEquals(20 * 20, zones.of(created[0].id).areaOf(ZoneType.RESIDENTIAL));
		assertEquals(60 * 60 - 20 * 20, zones.of(abc.id).areaOf(ZoneType.RESIDENTIAL));
	}
}
