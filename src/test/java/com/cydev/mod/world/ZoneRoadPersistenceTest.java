package com.cydev.mod.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cydev.mod.network.road.RoadKind;
import com.cydev.mod.network.road.RoadPos;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.zone.ZoneMap;
import com.cydev.mod.sim.zone.ZonePlot;
import com.cydev.mod.sim.zone.ZoneType;
import com.cydev.mod.world.persistence.RoadData;
import com.cydev.mod.world.persistence.ZoneData;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Zoning and the road network must load back exactly as they were saved. */
class ZoneRoadPersistenceTest {
	@SuppressWarnings("unchecked")
	private static <T> Codec<T> codec(Class<?> owner) throws Exception {
		Field f = owner.getDeclaredField("CODEC");
		f.setAccessible(true);
		return (Codec<T>) f.get(null);
	}

	@Test
	void zoneDataSurvivesSaveAndLoad() throws Exception {
		Codec<ZoneData> codec = codec(ZoneData.class);
		ZoneData data = new ZoneData();
		UUID cityA = UUID.randomUUID();
		UUID cityB = UUID.randomUUID();
		UUID by = UUID.randomUUID();
		ZoneMap a = data.registry().of(cityA);
		ZonePlot home = a.paint(Rect.of(-20, 5, 10, 40), ZoneType.RESIDENTIAL, by, 111);
		a.paint(Rect.of(0, 0, 4, 4), ZoneType.OFFICE, by, 222);
		data.registry().of(cityB).paint(Rect.of(100, 100, 120, 130), ZoneType.INDUSTRIAL, by, 333);

		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		ZoneData back = codec.parse(JsonOps.INSTANCE, json).getOrThrow();

		ZoneMap a2 = back.registry().of(cityA);
		assertEquals(a.plots.size(), a2.plots.size());
		assertEquals(ZoneType.RESIDENTIAL, a2.zoneAt(-20, 40));
		assertEquals(ZoneType.OFFICE, a2.zoneAt(2, 2));
		assertNull(a2.zoneAt(50, 50));
		ZonePlot home2 = a2.plotAt(-15, 30);
		assertNotNull(home2);
		assertEquals(home.id(), home2.id(), "plot ids are stable across saves");
		assertEquals(by, home2.zonedBy());
		assertEquals(111, home2.zonedAt());
		assertEquals(ZoneType.INDUSTRIAL, back.registry().of(cityB).zoneAt(110, 110));
		assertEquals(2, back.registry().all().size());
	}

	@Test
	void roadDataSurvivesSaveAndLoad() throws Exception {
		Codec<RoadData> codec = codec(RoadData.class);
		RoadData data = new RoadData();
		for (int x = -3; x <= 3; x++) {
			data.network().add(new RoadPos(x, 64, -7), RoadKind.ASPHALT);
		}
		data.network().add(new RoadPos(0, 64, -7), RoadKind.CROSSWALK);
		data.network().add(new RoadPos(1, 65, -7), RoadKind.CENTER_LINE);
		data.network().add(new RoadPos(40000, 319, -30000), RoadKind.LANE_DASHED);

		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		RoadData back = codec.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals(data.network().size(), back.network().size());
		assertEquals(RoadKind.CROSSWALK, back.network().kindAt(new RoadPos(0, 64, -7)));
		assertEquals(RoadKind.CENTER_LINE, back.network().kindAt(new RoadPos(1, 65, -7)));
		assertEquals(RoadKind.LANE_DASHED, back.network().kindAt(new RoadPos(40000, 319, -30000)));
		assertTrue(back.network().connected(new RoadPos(-3, 64, -7), new RoadPos(1, 65, -7)),
			"the loaded network is connected the same way");
		assertEquals(2, back.network().components().size());
	}

	@Test
	void emptyRoadDataLoadsEmpty() throws Exception {
		Codec<RoadData> codec = codec(RoadData.class);
		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, new RoadData()).getOrThrow();
		assertEquals(0, codec.parse(JsonOps.INSTANCE, json).getOrThrow().network().size());
	}
}
